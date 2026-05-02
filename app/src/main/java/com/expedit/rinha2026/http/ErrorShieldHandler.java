package com.expedit.rinha2026.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ErrorShieldHandler implements HttpHandler {
    private static final byte[] FALLBACK = "{\"approved\":false,\"fraud_score\":1.0}".getBytes(StandardCharsets.UTF_8);

    private final HttpHandler delegate;

    public ErrorShieldHandler(HttpHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            delegate.handle(exchange);
        } catch (Throwable t) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, FALLBACK.length);
            exchange.getResponseBody().write(FALLBACK);
            exchange.close();
        }
    }
}
