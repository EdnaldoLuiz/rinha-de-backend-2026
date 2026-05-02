package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;

public final class RuntimeBucketKeyEncoder {
    private RuntimeBucketKeyEncoder() {
    }

    public static int encode(short[] v) {
        int binaryBucket = binaryBucket(v[9], v[10], v[11]);
        int hourBucket = hourBucket(v[3]);
        int dayBucket = dayBucket(v[4]);
        int txBucket = txBucket(v[8]);
        return encode(binaryBucket, hourBucket, dayBucket, txBucket);
    }

    public static int encode(int binaryBucket, int hourBucket, int dayBucket, int txBucket) {
        return (((binaryBucket * Constants.HOURS) + hourBucket) * Constants.DAYS + dayBucket) * Constants.TX_BUCKETS + txBucket;
    }

    public static int binaryBucket(short isOnline, short cardPresent, short unknownMerchant) {
        int binaryBucket = 0;

        if (isOnline > 5_000) {
            binaryBucket |= 1;
        }
        if (cardPresent > 5_000) {
            binaryBucket |= 2;
        }
        if (unknownMerchant > 5_000) {
            binaryBucket |= 4;
        }

        return binaryBucket;
    }

    public static int hourBucket(short normalizedHour) {
        int hour = Math.round((normalizedHour * 23.0f) / Constants.VECTOR_SCALE);
        if (hour < 0) {
            return 0;
        }
        return Math.min(hour, Constants.HOURS - 1);
    }

    public static int dayBucket(short normalizedDay) {
        int day = Math.round((normalizedDay * 6.0f) / Constants.VECTOR_SCALE);
        if (day < 0) {
            return 0;
        }
        return Math.min(day, Constants.DAYS - 1);
    }

    public static int txBucket(short normalizedTxCount24h) {
        int bucket = normalizedTxCount24h / 2_500;
        if (bucket < 0) {
            return 0;
        }
        return Math.min(bucket, Constants.TX_BUCKETS - 1);
    }
}
