package com.expedit.rinha2026.http;

import com.expedit.rinha2026.bootstrap.AppConfig;
import com.expedit.rinha2026.bootstrap.ReadinessState;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpServerBootstrap {
    private static final int BACKLOG = 8192;

    public HttpServer start(AppConfig appConfig, ReadinessState readinessState, HttpRequestContext context) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(appConfig.port()), BACKLOG);
        server.createContext("/ready", new ErrorShieldHandler(new ReadyHandler(readinessState)));
        server.createContext("/fraud-score", new ErrorShieldHandler(new FraudScoreHandler(context)));
        server.setExecutor(Executors.newFixedThreadPool(appConfig.serverThreads(), new NamedThreadFactory()));
        server.start();
        return server;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "rinha-http-" + seq.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
