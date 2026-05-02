package com.expedit.rinha2026.bootstrap;

public final class AppMain {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnv();
        ReadinessState readinessState = new ReadinessState();
        new StartupLoader().start(config, readinessState);
    }
}
