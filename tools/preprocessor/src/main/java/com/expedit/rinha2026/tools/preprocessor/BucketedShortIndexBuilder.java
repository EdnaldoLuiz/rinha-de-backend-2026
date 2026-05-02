package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import java.util.List;

public final class BucketedShortIndexBuilder {

    public LoadedIndex build(List<ReferenceRecord> records) {
        int[] counts = new int[Constants.BUCKET_COUNT];

        for (ReferenceRecord record : records) {
            int bucket = record.bucketKey();
            if (bucket >= 0 && bucket < Constants.BUCKET_COUNT) {
                counts[bucket]++;
            }
        }

        int[] bucketStarts = new int[Constants.BUCKET_COUNT + 1];
        for (int i = 0; i < Constants.BUCKET_COUNT; i++) {
            bucketStarts[i + 1] = bucketStarts[i] + counts[i];
        }

        int vectorCount = records.size();
        short[] vectors = new short[vectorCount * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[vectorCount];

        int[] positions = bucketStarts.clone();

        for (ReferenceRecord record : records) {
            int bucket = record.bucketKey();
            int row = positions[bucket]++;
            int offset = row * Constants.VECTOR_DIMENSIONS;

            float[] vector = record.vector14();
            for (int d = 0; d < Constants.VECTOR_DIMENSIONS; d++) {
                vectors[offset + d] = quantize(vector[d]);
            }

            labels[row] = record.label();
        }

        return new LoadedIndex(
            Constants.VECTOR_DIMENSIONS,
            vectors,
            labels,
            bucketStarts
        );
    }

    private short quantize(float value) {
        int scaled = Math.round(value * Constants.VECTOR_SCALE);

        if (scaled > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (scaled < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) scaled;
    }
}
