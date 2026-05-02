package com.expedit.rinha2026.infra.diag;

public final class StartupDiagnostics {
    public void logReady(int port, int vectors) {
        System.out.printf("[startup] port=%d vectors=%d%n", port, vectors);
    }
}
