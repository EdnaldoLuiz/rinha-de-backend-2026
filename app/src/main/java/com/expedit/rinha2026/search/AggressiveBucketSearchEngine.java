package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;

public final class AggressiveBucketSearchEngine implements SearchEngine {
    private final short[] vectors;
    private final byte[] labels;
    private final int[] bucketStarts;
    private final ThreadLocal<Top5Selector> top5Local;
    private final ThreadLocal<boolean[]> visitedBuckets;

    public AggressiveBucketSearchEngine(short[] vectors, byte[] labels, int[] bucketStarts) {
        this.vectors = vectors;
        this.labels = labels;
        this.bucketStarts = bucketStarts == null ? new int[0] : bucketStarts;
        this.top5Local = ThreadLocal.withInitial(Top5Selector::new);
        this.visitedBuckets = ThreadLocal.withInitial(() -> new boolean[Constants.BUCKET_COUNT]);
    }

    @Override
    public SearchResult search(QueryVector queryVector) {
        SearchResult result = new SearchResult();
        if (labels.length == 0) {
            result.fraudCount = 0;
            result.worstDistance = Long.MAX_VALUE;
            return result;
        }

        Top5Selector top5 = top5Local.get();
        top5.reset();

        if (!hasValidBuckets()) {
            scanAll(queryVector, top5);
        } else {
            scanAggressiveWindow(queryVector, top5);
        }

        result.fraudCount = top5.fraudCount();
        result.worstDistance = top5.currentWorst();
        return result;
    }

    private boolean hasValidBuckets() {
        return bucketStarts.length == Constants.BUCKET_COUNT + 1 && bucketStarts[Constants.BUCKET_COUNT] == labels.length;
    }

    private void scanAll(QueryVector queryVector, Top5Selector top5) {
        for (int row = 0; row < labels.length; row++) {
            scanRowInline(queryVector.values, top5, row);
        }
    }

    private void scanAggressiveWindow(QueryVector queryVector, Top5Selector top5) {
        short[] q = queryVector.values;
        boolean[] visited = visitedBuckets.get();
        java.util.Arrays.fill(visited, false);

        int binaryBucket = RuntimeBucketKeyEncoder.binaryBucket(q[9], q[10], q[11]);
        int hourBucket = RuntimeBucketKeyEncoder.hourBucket(q[3]);
        int dayBucket = RuntimeBucketKeyEncoder.dayBucket(q[4]);
        int txBucket = RuntimeBucketKeyEncoder.txBucket(q[8]);

        int hourStart = Math.max(0, hourBucket - 1);
        int hourEnd = Math.min(Constants.HOURS - 1, hourBucket + 1);
        int txStart = Math.max(0, txBucket - 1);
        int txEnd = Math.min(Constants.TX_BUCKETS - 1, txBucket + 1);

        for (int hour = hourStart; hour <= hourEnd; hour++) {
            for (int tx = txStart; tx <= txEnd; tx++) {
                int bucketKey = RuntimeBucketKeyEncoder.encode(binaryBucket, hour, dayBucket, tx);
                visitBucket(q, top5, visited, bucketKey);
            }
        }
    }

    private void visitBucket(short[] query, Top5Selector top5, boolean[] visited, int bucketKey) {
        if (bucketKey < 0 || bucketKey >= Constants.BUCKET_COUNT || visited[bucketKey]) {
            return;
        }
        visited[bucketKey] = true;

        int start = bucketStarts[bucketKey];
        int end = bucketStarts[bucketKey + 1];
        for (int row = start; row < end; row++) {
            scanRowInline(query, top5, row);
        }
    }

    private void scanRowInline(short[] query, Top5Selector top5, int row) {
        int base = row * Constants.VECTOR_DIMENSIONS;
        long worst = top5.currentWorst();
        long distance = 0L;

        int d9 = vectors[base + 9] - query[9];
        distance += (long) d9 * d9;
        if (distance >= worst) return;
        int d10 = vectors[base + 10] - query[10];
        distance += (long) d10 * d10;
        if (distance >= worst) return;
        int d11 = vectors[base + 11] - query[11];
        distance += (long) d11 * d11;
        if (distance >= worst) return;
        int d5 = vectors[base + 5] - query[5];
        distance += (long) d5 * d5;
        if (distance >= worst) return;
        int d6 = vectors[base + 6] - query[6];
        distance += (long) d6 * d6;
        if (distance >= worst) return;
        int d7 = vectors[base + 7] - query[7];
        distance += (long) d7 * d7;
        if (distance >= worst) return;
        int d8 = vectors[base + 8] - query[8];
        distance += (long) d8 * d8;
        if (distance >= worst) return;
        int d12 = vectors[base + 12] - query[12];
        distance += (long) d12 * d12;
        if (distance >= worst) return;
        int d0 = vectors[base] - query[0];
        distance += (long) d0 * d0;
        if (distance >= worst) return;
        int d1 = vectors[base + 1] - query[1];
        distance += (long) d1 * d1;
        if (distance >= worst) return;
        int d2 = vectors[base + 2] - query[2];
        distance += (long) d2 * d2;
        if (distance >= worst) return;
        int d3 = vectors[base + 3] - query[3];
        distance += (long) d3 * d3;
        if (distance >= worst) return;
        int d4 = vectors[base + 4] - query[4];
        distance += (long) d4 * d4;
        if (distance >= worst) return;
        int d13 = vectors[base + 13] - query[13];
        distance += (long) d13 * d13;

        if (distance < worst) {
            top5.offer(distance, labels[row], row);
        }
    }
}
