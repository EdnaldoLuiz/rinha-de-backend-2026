package com.expedit.rinha2026.tools.preprocessor;

public final class BucketKeyEncoder {
    private static final int HOURS = 24;
    private static final int DAYS = 7;
    private static final int TX_BUCKETS = 4;

    public int encode(float[] v) {
        int binaryBucket = binaryBucket(v[9], v[10], v[11]);
        int hourBucket = hourBucket(v[3]);
        int dayBucket = dayBucket(v[4]);
        int txBucket = txBucket(v[8]);

        return (((binaryBucket * HOURS) + hourBucket) * DAYS + dayBucket) * TX_BUCKETS + txBucket;
    }

    private int binaryBucket(float isOnline, float cardPresent, float unknownMerchant) {
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

    private int hourBucket(float normalizedHour) {
        int hour = Math.round(normalizedHour * 23f);
        if (hour < 0) {
            return 0;
        }
        return Math.min(hour, HOURS - 1);
    }

    private int dayBucket(float normalizedDay) {
        int day = Math.round(normalizedDay * 6f);
        if (day < 0) {
            return 0;
        }
        return Math.min(day, DAYS - 1);
    }

    private int txBucket(float normalizedTxCount24h) {
        int scaled = Math.round(normalizedTxCount24h * 10000f);
        int bucket = scaled / 2500;
        if (bucket < 0) {
            return 0;
        }
        return Math.min(bucket, TX_BUCKETS - 1);
    }
}