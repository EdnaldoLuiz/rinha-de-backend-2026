package com.expedit.rinha2026.domain;

public final class Constants {
    public static final int VECTOR_DIMENSIONS = 14;
    public static final int VECTOR_SCALE = 10_000;

    public static final int HOURS = 24;
    public static final int DAYS = 7;
    public static final int TX_BUCKETS = 4;
    public static final int BINARY_BUCKETS = 8;
    public static final int BUCKET_COUNT = BINARY_BUCKETS * HOURS * DAYS * TX_BUCKETS;

    public static final int K = 5;
    public static final float FRAUD_THRESHOLD = 0.6f;

    private Constants() {
    }
}
