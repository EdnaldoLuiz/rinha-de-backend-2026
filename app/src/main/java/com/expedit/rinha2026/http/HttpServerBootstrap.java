package com.expedit.rinha2026.http;

import com.expedit.rinha2026.bootstrap.AppConfig;
import com.expedit.rinha2026.bootstrap.ReadinessState;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class HttpServerBootstrap {
    public HttpServer start(AppConfig appConfig, ReadinessState readinessState, HttpRequestContext context) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(appConfig.port()), 0);
        server.createContext("/ready", new ErrorShieldHandler(new ReadyHandler(readinessState)));
        server.createContext("/fraud-score", new ErrorShieldHandler(new FraudScoreHandler(context)));
        server.setExecutor(Executors.newFixedThreadPool(appConfig.serverThreads()));
        server.start();
        return server;
    }
}
