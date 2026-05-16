package com.expedit.rinha2026.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.infra.index.IvfIndex;
import org.junit.jupiter.api.Test;

class IvfKnnSearchEngineTest {
    @Test
    void shouldReturnTop5FromSingleCluster() {
        short[] vectors = new short[6 * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[] {0, 0, 1, 1, 1, 0};

        for (int i = 0; i < 6; i++) {
            int offset = i * Constants.VECTOR_DIMENSIONS;
            vectors[offset] = (short) (i * 1000);
        }

        IvfIndex index = new IvfIndex(
            Constants.VECTOR_DIMENSIONS,
            1,
            1,
            1,
            new float[Constants.VECTOR_DIMENSIONS],
            new short[Constants.VECTOR_DIMENSIONS],
            new short[Constants.VECTOR_DIMENSIONS],
            new int[] {0, 6},
            vectors,
            labels,
            new int[] {0, 1, 2, 3, 4, 5}
        );

        QueryVector q = new QueryVector();
        q.values[0] = 2100;

        SearchResult result = new IvfKnnSearchEngine(index).search(q);

        assertEquals(3, result.fraudCount);
    }

    @Test
    void shouldProbeMoreClustersWhenFastResultIsOnDecisionBoundary() {
        short[] vectors = new short[6 * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[] {1, 1, 0, 0, 0, 1};
        int[] anchors = new int[] {2000, 2200, 2100, 2300, 2400, 2500};

        for (int i = 0; i < anchors.length; i++) {
            int offset = i * Constants.VECTOR_DIMENSIONS;
            vectors[offset] = (short) anchors[i];
        }

        float[] centroids = new float[2 * Constants.VECTOR_DIMENSIONS];
        centroids[0] = 0.2f;
        centroids[1] = 0.9f;

        IvfIndex index = new IvfIndex(
            Constants.VECTOR_DIMENSIONS,
            2,
            1,
            2,
            centroids,
            new short[2 * Constants.VECTOR_DIMENSIONS],
            new short[2 * Constants.VECTOR_DIMENSIONS],
            new int[] {0, 2, 6},
            vectors,
            labels,
            new int[] {0, 1, 2, 3, 4, 5}
        );

        QueryVector q = new QueryVector();
        q.values[0] = 2100;

        SearchResult result = new IvfKnnSearchEngine(index).search(q);

        assertEquals(2, result.fraudCount);
    }
}
