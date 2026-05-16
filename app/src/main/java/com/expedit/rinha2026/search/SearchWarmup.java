package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.infra.index.IvfIndex;
import com.expedit.rinha2026.infra.index.LoadedIndex;

public final class SearchWarmup {
    private static final int ROW_STEP = 9_973;
    private static volatile float floatSink;
    private static volatile long longSink;

    public void warmIvf(IvfIndex index, SearchEngine searchEngine, int iterations) {
        if (iterations <= 0 || index.vectorCount() == 0) {
            return;
        }

        floatSink = touch(index.centroids());
        longSink = touch(index.offsets());
        warmVectors(index.vectors(), index.vectorCount(), searchEngine, iterations);
    }

    public void warmLoadedIndex(LoadedIndex index, SearchEngine searchEngine, int iterations) {
        if (iterations <= 0 || index.vectorCount() == 0) {
            return;
        }

        longSink = touch(index.bucketStarts());
        warmVectors(index.vectors(), index.vectorCount(), searchEngine, iterations);
    }

    private void warmVectors(short[] vectors, int vectorCount, SearchEngine searchEngine, int iterations) {
        QueryVector queryVector = new QueryVector();
        int runs = Math.max(iterations, vectorCount < iterations ? vectorCount : iterations);

        for (int i = 0; i < runs; i++) {
            int row = (int) (((long) i * ROW_STEP) % vectorCount);
            int offset = row * Constants.VECTOR_DIMENSIONS;
            System.arraycopy(vectors, offset, queryVector.values, 0, Constants.VECTOR_DIMENSIONS);
            searchEngine.search(queryVector);
        }
    }

    private float touch(float[] values) {
        float acc = 0f;
        for (float value : values) {
            acc += value;
        }
        return acc;
    }

    private long touch(int[] values) {
        long acc = 0L;
        for (int value : values) {
            acc += value;
        }
        return acc;
    }
}
