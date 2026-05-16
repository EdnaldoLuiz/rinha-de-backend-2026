package com.expedit.rinha2026.http;

import com.expedit.rinha2026.bootstrap.ReadinessState;
import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.fallback.FallbackDecision;
import com.expedit.rinha2026.search.IvfKnnSearchEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public final class NettyRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final byte[] BAD_REQUEST = "{\"error\":\"invalid payload\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] READY_BODY = "ready".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_FOUND = "not found".getBytes(StandardCharsets.UTF_8);
    private static final AsciiString JSON_CONTENT_TYPE = AsciiString.cached("application/json");
    private static final AsciiString TEXT_CONTENT_TYPE = AsciiString.cached("text/plain; charset=utf-8");
    private static final boolean FALLBACK_STATS = "1".equals(System.getenv().getOrDefault("FALLBACK_STATS", "0"));
    private static final boolean RUNTIME_MISMATCH_DEBUG = "1".equals(System.getenv().getOrDefault("RUNTIME_MISMATCH_DEBUG", "0"));
    private static final LongAdder REQUESTS = new LongAdder();
    private static final LongAdder FALLBACKS = new LongAdder();
    private static final RuntimeMismatchLogger MISMATCH_LOGGER =
        RUNTIME_MISMATCH_DEBUG ? RuntimeMismatchLogger.loadFromEnv() : null;
    private static final Set<String> DEBUG_TX_IDS = parseDebugTxIds();

    private final ReadinessState readinessState;
    private final HttpRequestContext context;
    private final ThreadLocal<QueryVector> queryVectors = ThreadLocal.withInitial(QueryVector::new);
    private final ThreadLocal<byte[]> bodyBuffers = ThreadLocal.withInitial(() -> new byte[8192]);

    public NettyRequestHandler(ReadinessState readinessState, HttpRequestContext context) {
        this.readinessState = readinessState;
        this.context = context;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (FALLBACK_STATS) {
            REQUESTS.increment();
            long requestCount = REQUESTS.sum();
            if (requestCount % 5_000 == 0) {
                long fallbackCount = FALLBACKS.sum();
                System.out.printf(
                    "Fallback stats: requests=%d fallbacks=%d rate=%.5f%%%n",
                    requestCount,
                    fallbackCount,
                    (fallbackCount * 100.0) / requestCount
                );
            }
        }

        String uri = request.uri();
        int pathEnd = uri.indexOf('?');
        String path = pathEnd >= 0 ? uri.substring(0, pathEnd) : uri;

        if ("/ready".equals(path)) {
            byte[] body = readinessState.isReady() ? READY_BODY : NOT_FOUND;
            HttpResponseStatus status = readinessState.isReady() ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE;
            writeResponse(ctx, request, status, body, TEXT_CONTENT_TYPE);
            return;
        }

        if (!"/fraud-score".equals(path)) {
            writeResponse(ctx, request, HttpResponseStatus.NOT_FOUND, NOT_FOUND, TEXT_CONTENT_TYPE);
            return;
        }

        if (!HttpMethod.POST.equals(request.method())) {
            writeResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, Unpooled.EMPTY_BUFFER, TEXT_CONTENT_TYPE);
            return;
        }

        FraudRequestFields fraudRequest;
        String txId = null;
        int predictedFraudCount = -1;
        int bodyLength = 0;
        int bodyHash = 0;
        byte[] requestBody = null;
        try {
            ByteBuf content = request.content();
            bodyLength = content.readableBytes();
            byte[] body = bodyBuffers.get();
            if (bodyLength > body.length) {
                int newSize = body.length;
                while (newSize < bodyLength) {
                    newSize <<= 1;
                }
                body = new byte[newSize];
                bodyBuffers.set(body);
            }
            content.getBytes(content.readerIndex(), body, 0, bodyLength);
            requestBody = body;
            if (MISMATCH_LOGGER != null) {
                txId = RuntimeMismatchLogger.extractTransactionId(body, bodyLength);
            }
            fraudRequest = context.parser().parse(body, bodyLength);
        } catch (RuntimeException parseError) {
            writeResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, BAD_REQUEST, JSON_CONTENT_TYPE);
            return;
        }

        QueryVector queryVector = queryVectors.get();
        try {
            context.vectorizer().vectorize(fraudRequest, queryVector);
            SearchResult result = context.searchEngine().search(queryVector);
            predictedFraudCount = result.fraudCount;
            writeResponse(ctx, request, HttpResponseStatus.OK, context.responseBuffers().byFraudCount(predictedFraudCount), JSON_CONTENT_TYPE);
        } catch (Throwable throwable) {
            if (FALLBACK_STATS) {
                FALLBACKS.increment();
                long requestCount = REQUESTS.sum();
                long fallbackCount = FALLBACKS.sum();
                if (fallbackCount <= 5) {
                    System.out.printf(
                        "Fallback event: requests=%d fallbacks=%d cause=%s message=%s%n",
                        requestCount,
                        fallbackCount,
                        throwable.getClass().getName(),
                        throwable.getMessage()
                    );
                }
            }
            FallbackDecision fallback = context.fallbackScorer().score(fraudRequest);
            predictedFraudCount = fallback.fraudCount();
            writeResponse(ctx, request, HttpResponseStatus.OK, context.responseBuffers().byFraudCount(predictedFraudCount), JSON_CONTENT_TYPE);
        }

        if (MISMATCH_LOGGER != null && predictedFraudCount >= 0) {
            boolean actualApproved = predictedFraudCount < 3;
            MISMATCH_LOGGER.checkAndLog(txId, actualApproved, predictedFraudCount);
        }

        if (shouldDebugTx(txId)) {
            bodyHash = 1;
            for (int i = 0; i < bodyLength; i++) {
                bodyHash = (31 * bodyHash) + requestBody[i];
            }
            String thread = Thread.currentThread().getName();
            System.out.printf(
                "RuntimeDebugTx: txId=%s fraudCount=%d approved=%s thread=%s bodyLen=%d bodyHash=%d query=%s%n",
                txId,
                predictedFraudCount,
                predictedFraudCount < 3,
                thread,
                bodyLength,
                bodyHash,
                Arrays.toString(queryVector.values)
            );

            if (context.searchEngine() instanceof IvfKnnSearchEngine ivf) {
                IvfKnnSearchEngine.DebugTop5Snapshot top5 = ivf.currentThreadTop5Snapshot();
                System.out.printf(
                    "RuntimeDebugTxTop5: txId=%s distances=%s labels=%s ids=%s%n",
                    txId,
                    Arrays.toString(top5.distances()),
                    Arrays.toString(top5.labels()),
                    Arrays.toString(top5.ids())
                );
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private void writeResponse(
        ChannelHandlerContext ctx,
        FullHttpRequest request,
        HttpResponseStatus status,
        byte[] body,
        AsciiString contentType
    ) {
        writeResponse(ctx, request, status, Unpooled.wrappedBuffer(body), contentType);
    }

    private void writeResponse(
        ChannelHandlerContext ctx,
        FullHttpRequest request,
        HttpResponseStatus status,
        ByteBuf body,
        AsciiString contentType
    ) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());

        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
            return;
        }

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static Set<String> parseDebugTxIds() {
        String raw = System.getenv().getOrDefault("RUNTIME_DEBUG_TX_IDS", "");
        if (raw.isBlank()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static boolean shouldDebugTx(String txId) {
        return txId != null && !DEBUG_TX_IDS.isEmpty() && DEBUG_TX_IDS.contains(txId);
    }
}
