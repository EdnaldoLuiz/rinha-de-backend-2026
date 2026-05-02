package com.expedit.rinha2026.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import org.junit.jupiter.api.Test;

class ExactKnnSearchEngineTest {

    @Test
    void shouldReturnFraudCountFromTop5WithoutBuckets() {
        short[] vectors = new short[6 * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[] {0, 0, 1, 1, 1, 0};

        for (int i = 0; i < 6; i++) {
            int offset = i * Constants.VECTOR_DIMENSIONS;
            vectors[offset] = (short) (i * 1000);
        }

        QueryVector q = new QueryVector();
        q.values[0] = 2100;

        ExactKnnSearchEngine engine = new ExactKnnSearchEngine(vectors, labels);
        SearchResult result = engine.search(q);

        assertEquals(3, result.fraudCount);
    }

    @Test
    void shouldUseBucketStartsWhenAvailable() {
        short[] vectors = new short[8 * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[] {0, 0, 1, 1, 1, 0, 1, 0};

        for (int i = 0; i < 8; i++) {
            int offset = i * Constants.VECTOR_DIMENSIONS;
            vectors[offset] = (short) (i * 1000);
        }

        int[] bucketStarts = new int[Constants.BUCKET_COUNT + 1];
        bucketStarts[0] = 0;
        bucketStarts[1] = 8;
        for (int i = 2; i < bucketStarts.length; i++) {
            bucketStarts[i] = 8;
        }

        QueryVector q = new QueryVector();
        q.values[0] = 2100;
        q.values[3] = 0;
        q.values[4] = 0;
        q.values[8] = 0;
        q.values[9] = 0;
        q.values[10] = 0;
        q.values[11] = 0;

        ExactKnnSearchEngine engine = new ExactKnnSearchEngine(vectors, labels, bucketStarts);
        SearchResult result = engine.search(q);

        assertEquals(3, result.fraudCount);
    }
}
