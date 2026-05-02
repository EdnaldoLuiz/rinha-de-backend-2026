package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.infra.index.BucketMetadata;

public final class BucketScanPlanner {
    public int[] plan(BucketMetadata[] buckets, QueryVector queryVector) {
        int size = buckets == null ? 0 : buckets.length;
        int[] order = new int[size];
        float[] bounds = new float[size];
        plan(buckets, queryVector, order, bounds);
        return order;
    }

    public int plan(BucketMetadata[] buckets, QueryVector queryVector, int[] order, float[] bounds) {
        return plan(buckets, queryVector, order, bounds, Float.POSITIVE_INFINITY, -1);
    }

    public int plan(
        BucketMetadata[] buckets,
        QueryVector queryVector,
        int[] order,
        float[] bounds,
        float maxLowerBound,
        int skipBucketIndex
    ) {
        int size = buckets == null ? 0 : buckets.length;
        int count = 0;

        for (int i = 0; i < size; i++) {
            if (i == skipBucketIndex) {
                continue;
            }

            float lowerBound = BucketLowerBound.lowerBound(queryVector, buckets[i]);
            if (lowerBound < maxLowerBound) {
                order[count] = i;
                bounds[count] = lowerBound;
                count++;
            }
        }

        for (int i = 1; i < count; i++) {
            int idx = order[i];
            float b = bounds[i];
            int j = i - 1;
            while (j >= 0 && bounds[j] > b) {
                bounds[j + 1] = bounds[j];
                order[j + 1] = order[j];
                j--;
            }
            bounds[j + 1] = b;
            order[j + 1] = idx;
        }

        return count;
    }
}
