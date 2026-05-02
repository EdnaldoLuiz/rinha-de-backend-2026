package com.expedit.rinha2026.infra.diag;

public final class LocalStats {
    private long requests;

    public void incrementRequests() {
        requests++;
    }

    public long requests() {
        return requests;
    }
}
