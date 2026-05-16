package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;

public record IvfIndex(
    int dimensions,
    int clusters,
    int fastNprobe,
    int fullNprobe,
    float[] centroids,
    short[] bboxMin,
    short[] bboxMax,
    int[] offsets,
    short[] vectors,
    byte[] labels,
    int[] origIds
) {
    public static IvfIndex empty() {
        return new IvfIndex(
            Constants.VECTOR_DIMENSIONS,
            0,
            0,
            0,
            new float[0],
            new short[0],
            new short[0],
            new int[] {0},
            new short[0],
            new byte[0],
            new int[0]
        );
    }

    public int vectorCount() {
        return labels.length;
    }
}
