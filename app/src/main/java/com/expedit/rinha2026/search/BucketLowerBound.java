package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.infra.index.BucketMetadata;

public final class BucketLowerBound {
    private BucketLowerBound() {
    }

    public static float lowerBound(QueryVector queryVector, BucketMetadata bucket) {
        short[] q = queryVector.values;
        float sum = 0f;
        for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
            float v = q[i] / (float) Constants.VECTOR_SCALE;
            float min = bucket.minBounds()[i];
            float max = bucket.maxBounds()[i];
            float delta = 0f;
            if (v < min) {
                delta = min - v;
            } else if (v > max) {
                delta = v - max;
            }
            sum += delta * delta;
        }
        return sum;
    }
}
