package com.expedit.rinha2026.http;

import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.fallback.FallbackDecision;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class FraudScoreHandler implements HttpHandler {
    private static final byte[] BAD_REQUEST = "{\"error\":\"invalid payload\"}".getBytes(StandardCharsets.UTF_8);

    private final HttpRequestContext ctx;
    private final ThreadLocal<QueryVector> queryVectors = ThreadLocal.withInitial(QueryVector::new);

    public FraudScoreHandler(HttpRequestContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        FraudRequestFields req;
        try {
            req = ctx.parser().parse(body);
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
            FallbackDecision fallback = ctx.fallbackScorer().score(req);
            ctx.responseWriter().writeJson(exchange, 200, ctx.responseBuffers().byFraudCount(fallback.fraudCount()));
        }
    }
}
