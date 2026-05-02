package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.BucketMetadata;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class BucketBoundsCalculator {
    public LoadedIndex buildIndexed(List<ReferenceRecord> records) {
        Map<Integer, List<ReferenceRecord>> grouped = new BucketAccumulator().groupByBucket(records);

        int vectorCount = records.size();
        float[] vectors = new float[vectorCount * Constants.VECTOR_STRIDE];
        byte[] labels = new byte[vectorCount];
        List<BucketMetadata> buckets = new ArrayList<>();

        int row = 0;
        for (Map.Entry<Integer, List<ReferenceRecord>> entry : grouped.entrySet()) {
            int key = entry.getKey();
            List<ReferenceRecord> bucketRecords = entry.getValue();
            int start = row;

            float[] minBounds = new float[Constants.VECTOR_DIMENSIONS];
            float[] maxBounds = new float[Constants.VECTOR_DIMENSIONS];
            Arrays.fill(minBounds, Float.POSITIVE_INFINITY);
            Arrays.fill(maxBounds, Float.NEGATIVE_INFINITY);

            for (ReferenceRecord record : bucketRecords) {
                float[] vector14 = record.vector14();
                int offset = row * Constants.VECTOR_STRIDE;
                for (int d = 0; d < Constants.VECTOR_DIMENSIONS; d++) {
                    float value = vector14[d];
                    vectors[offset + d] = value;
                    if (value < minBounds[d]) {
                        minBounds[d] = value;
                    }
                    if (value > maxBounds[d]) {
                        maxBounds[d] = value;
                    }
                }
                vectors[offset + 14] = 0f;
                vectors[offset + 15] = 0f;
                labels[row] = record.label();
                row++;
            }

            buckets.add(new BucketMetadata(key, start, bucketRecords.size(), minBounds, maxBounds));
        }

        return new LoadedIndex(
            Constants.VECTOR_DIMENSIONS,
            Constants.VECTOR_STRIDE,
            vectors,
            labels,
            buckets.toArray(new BucketMetadata[0])
        );
    }
}
