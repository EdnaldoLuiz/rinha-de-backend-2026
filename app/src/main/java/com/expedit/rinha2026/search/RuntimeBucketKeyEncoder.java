package com.expedit.rinha2026.search;

public final class RuntimeBucketKeyEncoder {
    private static final int HOURS = 24;
    private static final int DAYS = 7;
    private static final int TX_BUCKETS = 4;

    private RuntimeBucketKeyEncoder() {
    }

    public static int encode(float[] v) {
        int binaryBucket = binaryBucket(v[9], v[10], v[11]);
        int hourBucket = hourBucket(v[3]);
        int dayBucket = dayBucket(v[4]);
        int txBucket = txBucket(v[8]);

        return bucket(binaryBucket, hourBucket, dayBucket, txBucket);
    }

    public static int encode(int binaryBucket, int hourBucket, int dayBucket, int txBucket) {
        return bucket(binaryBucket, hourBucket, dayBucket, txBucket);
    }

    public static int binaryBucket(float isOnline, float cardPresent, float unknownMerchant) {
        int binaryBucket = 0;

        if (isOnline >= 0.5f) {
            binaryBucket |= 1;
        }
        if (cardPresent >= 0.5f) {
            binaryBucket |= 2;
        }
        if (unknownMerchant >= 0.5f) {
            binaryBucket |= 4;
        }

        return binaryBucket;
    }

    public static int hourBucket(float normalizedHour) {
        int hour = Math.round(normalizedHour * 23f);
        if (hour < 0) {
            return 0;
        }
        return Math.min(hour, HOURS - 1);
    }

    public static int dayBucket(float normalizedDay) {
        int day = Math.round(normalizedDay * 6f);
        if (day < 0) {
            return 0;
        }
        return Math.min(day, DAYS - 1);
    }

    public static int txBucket(float normalizedTxCount24h) {
        int scaled = Math.round(normalizedTxCount24h * 10000f);
        int bucket = scaled / 2500;
        if (bucket < 0) {
            return 0;
        }
        return Math.min(bucket, TX_BUCKETS - 1);
    }

    private static int bucket(int binaryBucket, int hour, int day, int txBucket) {
        return (((binaryBucket * HOURS) + hour) * DAYS + day) * TX_BUCKETS + txBucket;
    }
}