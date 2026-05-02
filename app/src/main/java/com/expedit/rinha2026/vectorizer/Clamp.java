package com.expedit.rinha2026.vectorizer;

public final class Clamp {
    private Clamp() {
    }

    public static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
