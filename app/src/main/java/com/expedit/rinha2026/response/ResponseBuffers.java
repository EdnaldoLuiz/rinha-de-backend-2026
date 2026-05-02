package com.expedit.rinha2026.response;

public final class ResponseBuffers {
    private final byte[][] responses;

    public ResponseBuffers() {
        this.responses = new byte[][] {
            json(true, 0.0f).getBytes(),
            json(true, 0.2f).getBytes(),
            json(true, 0.4f).getBytes(),
            json(false, 0.6f).getBytes(),
            json(false, 0.8f).getBytes(),
            json(false, 1.0f).getBytes()
        };
    }

    public byte[] byFraudCount(int fraudCount) {
        if (fraudCount < 0) {
            return responses[0];
        }
        if (fraudCount > 5) {
            return responses[5];
        }
        return responses[fraudCount];
    }

    private static String json(boolean approved, float fraudScore) {
        return "{\"approved\":" + approved + ",\"fraud_score\":" + fraudScore + "}";
    }
}
