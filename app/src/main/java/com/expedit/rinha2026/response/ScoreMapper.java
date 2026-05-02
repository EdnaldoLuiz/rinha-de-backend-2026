package com.expedit.rinha2026.response;

public final class ScoreMapper {
    private static final float[] SCORES = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};

    public float fraudScoreFromFraudCount(int fraudCount) {
        if (fraudCount < 0) {
            return 0.0f;
        }
        if (fraudCount > 5) {
            return 1.0f;
        }
        return SCORES[fraudCount];
    }

    public boolean approved(int fraudCount) {
        return fraudScoreFromFraudCount(fraudCount) < 0.6f;
    }
}
