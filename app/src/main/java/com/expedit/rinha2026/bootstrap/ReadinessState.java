package com.expedit.rinha2026.bootstrap;

public final class ReadinessState {
    private volatile boolean ready;

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
