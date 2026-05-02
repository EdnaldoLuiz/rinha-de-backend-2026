package com.expedit.rinha2026.search;

public final class DistanceKernel {
    private DistanceKernel() {
    }

    public static float squaredDistanceEarlyAbort(float[] query, float[] vectors, int offset, float currentWorst) {
        float dist = 0f;
        for (int i = 0; i < 14; i++) {
            float d = query[i] - vectors[offset + i];
            dist += d * d;
            if (dist >= currentWorst) {
                return dist;
            }
        }
        return dist;
    }
}
