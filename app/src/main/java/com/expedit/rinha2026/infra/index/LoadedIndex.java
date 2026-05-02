package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;

public record LoadedIndex(
    int dimensions,
    int stride,
    float[] vectors,
    byte[] labels,
    BucketMetadata[] buckets
) {
    public static LoadedIndex fromVectors(float[] vectors, byte[] labels) {
        return new LoadedIndex(
            Constants.VECTOR_DIMENSIONS,
            Constants.VECTOR_STRIDE,
            vectors,
            labels,
            new BucketMetadata[0]
        );
    }

    public static LoadedIndex empty() {
        return fromVectors(new float[0], new byte[0]);
    }
}
