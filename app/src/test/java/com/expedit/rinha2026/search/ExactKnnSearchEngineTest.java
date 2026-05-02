package com.expedit.rinha2026.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.infra.index.BucketMetadata;
import org.junit.jupiter.api.Test;

class ExactKnnSearchEngineTest {
    @Test
    void shouldReturnFraudCountFromTop5() {
        float[] vectors = new float[6 * Constants.VECTOR_STRIDE];
        byte[] labels = new byte[] {0, 0, 1, 1, 1, 0};

        for (int i = 0; i < 6; i++) {
            int offset = i * Constants.VECTOR_STRIDE;
            vectors[offset] = i; // distance anchor in first dimension
        }

        QueryVector q = new QueryVector();
        q.values[0] = 2.1f;

        ExactKnnSearchEngine engine = new ExactKnnSearchEngine(vectors, labels);
        SearchResult result = engine.search(q);

        // nearest 5 to 2.1 are ids 2,3,1,4,0 -> labels 1,1,0,1,0 => 3 frauds
        assertEquals(3, result.fraudCount);
    }

    @Test
    void shouldReturnSameTop5UsingBucketMetadata() {
        float[] vectors = new float[8 * Constants.VECTOR_STRIDE];
        byte[] labels = new byte[] {0, 0, 1, 1, 1, 0, 1, 0};

        for (int i = 0; i < 8; i++) {
            int offset = i * Constants.VECTOR_STRIDE;
            vectors[offset] = i;
        }

        BucketMetadata nearBucket = new BucketMetadata(
            10,
            0,
            5,
            boundsWithFirstDimension(0f, 0f),
            boundsWithFirstDimension(4f, 1f)
        );
        BucketMetadata farBucket = new BucketMetadata(
            20,
            5,
            3,
            boundsWithFirstDimension(5f, 0f),
            boundsWithFirstDimension(7f, 1f)
        );

        QueryVector q = new QueryVector();
        q.values[0] = 2.1f;

        ExactKnnSearchEngine engine = new ExactKnnSearchEngine(vectors, labels, new BucketMetadata[] {farBucket, nearBucket});
        SearchResult result = engine.search(q);

        // nearest 5 to 2.1 are ids 2,3,1,4,0 -> labels 1,1,0,1,0 => 3 frauds
        assertEquals(3, result.fraudCount);
    }

    @Test
    void shouldPreserveExactResultWhenQueryBucketIsScannedFirst() {
        float[] vectors = new float[8 * Constants.VECTOR_STRIDE];
        byte[] labels = new byte[] {0, 0, 0, 0, 0, 1, 1, 1};
        float[] anchors = new float[] {0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.05f, 0.06f, 0.90f};

        for (int i = 0; i < anchors.length; i++) {
            int offset = i * Constants.VECTOR_STRIDE;
            vectors[offset] = anchors[i];
        }

        BucketMetadata queryBucket = new BucketMetadata(
            0,
            0,
            5,
            boundsWithFirstDimension(0.10f, 0f),
            boundsWithFirstDimension(0.50f, 0f)
        );
        BucketMetadata closerBucket = new BucketMetadata(
            128,
            5,
            2,
            boundsWithFirstDimension(0.05f, 0f),
            boundsWithFirstDimension(0.06f, 0f)
        );
        BucketMetadata farBucket = new BucketMetadata(
            256,
            7,
            1,
            boundsWithFirstDimension(0.90f, 0f),
            boundsWithFirstDimension(0.90f, 0f)
        );

        QueryVector q = new QueryVector();

        SearchResult linear = new ExactKnnSearchEngine(vectors, labels).search(q);
        SearchResult bucketed = new ExactKnnSearchEngine(
            vectors,
            labels,
            new BucketMetadata[] {farBucket, closerBucket, queryBucket}
        ).search(q);

        assertEquals(linear.fraudCount, bucketed.fraudCount);
        assertEquals(2, bucketed.fraudCount);
    }

    private static float[] boundsWithFirstDimension(float firstDimension, float fillForOthers) {
        float[] bounds = new float[Constants.VECTOR_DIMENSIONS];
        for (int i = 1; i < Constants.VECTOR_DIMENSIONS; i++) {
            bounds[i] = fillForOthers;
        }
        bounds[0] = firstDimension;
        return bounds;
    }
}
