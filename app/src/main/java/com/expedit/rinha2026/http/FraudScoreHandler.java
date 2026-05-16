package com.expedit.rinha2026.http;

import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.fallback.FallbackDecision;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.LongAdder;

public final class FraudScoreHandler implements HttpHandler {
    private static final byte[] BAD_REQUEST = "{\"error\":\"invalid payload\"}".getBytes(StandardCharsets.UTF_8);
    private static final int INITIAL_BODY_BUFFER_SIZE = 8192;
    private static final boolean FALLBACK_STATS = "1".equals(System.getenv().getOrDefault("FALLBACK_STATS", "0"));
    private static final LongAdder REQUESTS = new LongAdder();
    private static final LongAdder FALLBACKS = new LongAdder();

    private final HttpRequestContext ctx;
    private final ThreadLocal<QueryVector> queryVectors = ThreadLocal.withInitial(QueryVector::new);
    private final ThreadLocal<byte[]> bodyBuffers = ThreadLocal.withInitial(() -> new byte[INITIAL_BODY_BUFFER_SIZE]);

    public FraudScoreHandler(HttpRequestContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (FALLBACK_STATS) {
            REQUESTS.increment();
            long requestCount = REQUESTS.sum();
            if (requestCount % 5_000 == 0) {
                long fb = FALLBACKS.sum();
                System.out.printf(
                    "Fallback stats: requests=%d fallbacks=%d rate=%.5f%%%n",
                    requestCount,
                    fb,
                    (fb * 100.0) / requestCount
                );
            }
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] bodyBuffer = bodyBuffers.get();
        int bodyLength;

        try {
            BodyReadResult body = readBody(exchange.getRequestBody(), bodyBuffer);
            bodyBuffer = body.buffer();
            bodyLength = body.length();
            if (bodyBuffer != bodyBuffers.get()) {
                bodyBuffers.set(bodyBuffer);
            }
        } catch (RuntimeException readError) {
            ctx.responseWriter().writeJson(exchange, 400, BAD_REQUEST);
            return;
        }

        FraudRequestFields req;
        try {
            req = ctx.parser().parse(bodyBuffer, bodyLength);
        } catch (RuntimeException parseError) {
            ctx.responseWriter().writeJson(exchange, 400, BAD_REQUEST);
            return;
        }

        QueryVector queryVector = queryVectors.get();
        try {
            ctx.vectorizer().vectorize(req, queryVector);
            SearchResult result = ctx.searchEngine().search(queryVector);
            ctx.responseWriter().writeJson(exchange, 200, ctx.responseBuffers().byFraudCount(result.fraudCount));
        } catch (Throwable t) {
            if (FALLBACK_STATS) {
                FALLBACKS.increment();
                long requestCount = REQUESTS.sum();
                long fallbackCount = FALLBACKS.sum();
                if (fallbackCount <= 5) {
                    System.out.printf(
                        "Fallback event: requests=%d fallbacks=%d cause=%s message=%s%n",
                        requestCount,
                        fallbackCount,
                        t.getClass().getName(),
                        t.getMessage()
                    );
                }
            }
            FallbackDecision fallback = ctx.fallbackScorer().score(req);
            ctx.responseWriter().writeJson(exchange, 200, ctx.responseBuffers().byFraudCount(fallback.fraudCount()));
        }
    }

    private BodyReadResult readBody(InputStream input, byte[] buffer) throws IOException {
        int length = 0;

        while (true) {
            if (length == buffer.length) {
                byte[] grown = new byte[buffer.length << 1];
                System.arraycopy(buffer, 0, grown, 0, buffer.length);
                buffer = grown;
            }

            int read = input.read(buffer, length, buffer.length - length);
            if (read < 0) {
                return new BodyReadResult(buffer, length);
            }
            length += read;
        }
    }

    private record BodyReadResult(byte[] buffer, int length) {
    }
}
