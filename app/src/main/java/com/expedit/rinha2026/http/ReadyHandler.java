package com.expedit.rinha2026.http;

import com.expedit.rinha2026.bootstrap.ReadinessState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ReadyHandler implements HttpHandler {
    private static final byte[] READY = "{\"status\":\"ready\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_READY = "{\"status\":\"starting\"}".getBytes(StandardCharsets.UTF_8);

    private final ReadinessState readinessState;

    public ReadyHandler(ReadinessState readinessState) {
        this.readinessState = readinessState;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] body = readinessState.isReady() ? READY : NOT_READY;
        int status = readinessState.isReady() ? 200 : 503;
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
