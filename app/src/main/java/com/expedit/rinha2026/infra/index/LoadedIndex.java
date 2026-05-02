package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;

public record LoadedIndex(
    int dimensions,
    short[] vectors,
    byte[] labels,
    int[] bucketStarts
) {
    public static LoadedIndex empty() {
        return new LoadedIndex(
            Constants.VECTOR_DIMENSIONS,
            new short[0],
            new byte[0],
            new int[Constants.BUCKET_COUNT + 1]
        );
    }

    public int vectorCount() {
        return labels.length;
    }
}
