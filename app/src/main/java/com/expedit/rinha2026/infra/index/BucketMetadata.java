package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;

public final class BucketMetadata {
    private final int key;
    private final int startVector;
    private final int count;
    private final float[] minBounds;
    private final float[] maxBounds;

    public BucketMetadata(int key, int startVector, int count, float[] minBounds, float[] maxBounds) {
        this.key = key;
        this.startVector = startVector;
        this.count = count;
        this.minBounds = minBounds;
        this.maxBounds = maxBounds;
    }

    public int key() {
        return key;
    }

    public int startVector() {
        return startVector;
    }

    public int count() {
        return count;
    }

    public float[] minBounds() {
        return minBounds;
    }

    public float[] maxBounds() {
        return maxBounds;
    }

    public static BucketMetadata empty() {
        return new BucketMetadata(0, 0, 0, new float[Constants.VECTOR_DIMENSIONS], new float[Constants.VECTOR_DIMENSIONS]);
    }
}
