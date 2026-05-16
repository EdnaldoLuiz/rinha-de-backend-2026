package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import java.util.Arrays;

public final class ExactKnnSearchEngine implements SearchEngine {
    private final short[] vectors;
    private final byte[] labels;
    private final int[] bucketStarts;
    private final int[] origIds;
    private final ThreadLocal<Top5Selector> top5Local;
    private final ThreadLocal<boolean[]> visitedBuckets;

    public ExactKnnSearchEngine(short[] vectors, byte[] labels) {
        this(vectors, labels, null, null);
    }

    public ExactKnnSearchEngine(short[] vectors, byte[] labels, int[] bucketStarts) {
        this(vectors, labels, bucketStarts, null);
    }

    private ExactKnnSearchEngine(short[] vectors, byte[] labels, int[] bucketStarts, int[] origIds) {
        this.vectors = vectors;
        this.labels = labels;
        this.bucketStarts = bucketStarts == null ? new int[0] : bucketStarts;
        this.origIds = origIds == null ? new int[0] : origIds;
        this.top5Local = ThreadLocal.withInitial(Top5Selector::new);
        this.visitedBuckets = ThreadLocal.withInitial(() -> new boolean[Constants.BUCKET_COUNT]);
    }

    public static ExactKnnSearchEngine forIvf(short[] vectors, byte[] labels, int[] origIds) {
        return new ExactKnnSearchEngine(vectors, labels, null, origIds);
    }

    public static ExactKnnSearchEngine empty() {
        return new ExactKnnSearchEngine(new short[0], new byte[0], new int[0], new int[0]);
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

        if (bucketStarts.length == Constants.BUCKET_COUNT + 1
            && bucketStarts[Constants.BUCKET_COUNT] == labels.length) {
            scanNeighborhood(queryVector, top5);
        } else {
            scanAll(queryVector, top5);
        }

        result.fraudCount = top5.fraudCount();
        result.worstDistance = top5.currentWorst();
        return result;
    }

    private void scanAll(QueryVector queryVector, Top5Selector top5) {
        for (int row = 0; row < labels.length; row++) {
            scanRow(queryVector, top5, row);
        }
    }

    private void scanNeighborhood(QueryVector queryVector, Top5Selector top5) {
        short[] q = queryVector.values;
        boolean[] visited = visitedBuckets.get();
        Arrays.fill(visited, false);

        int binaryBucket = RuntimeBucketKeyEncoder.binaryBucket(q[9], q[10], q[11]);
        int hourBucket = RuntimeBucketKeyEncoder.hourBucket(q[3]);
        int dayBucket = RuntimeBucketKeyEncoder.dayBucket(q[4]);
        int txBucket = RuntimeBucketKeyEncoder.txBucket(q[8]);

        int exactKey = RuntimeBucketKeyEncoder.encode(binaryBucket, hourBucket, dayBucket, txBucket);
        visitBucket(queryVector, top5, visited, exactKey);
        if (top5.isFull()) {
            return;
        }

        // Camada 1: vizinhanca curta
        visitHourDayTxWindow(queryVector, top5, visited, binaryBucket, hourBucket, dayBucket, txBucket, 1, 1, 1, exactKey);
        if (top5.isFull()) {
            return;
        }

        // Camada 2: mesmo dia, horas proximas, todos os tx
        visitSameDayHourWindowAllTx(queryVector, top5, visited, binaryBucket, hourBucket, dayBucket, 2);
        if (top5.isFull()) {
            return;
        }

        // Camada 3: mesma hora, dias proximos, tx proximo
        visitSameHourDayWindowTxWindow(queryVector, top5, visited, binaryBucket, hourBucket, dayBucket, txBucket, 2, 1);
        if (top5.isFull()) {
            return;
        }

        // Camada 4: mesmo dia, todas as horas, todos os tx
        visitSameDayAllHoursAllTx(queryVector, top5, visited, binaryBucket, dayBucket);
        if (top5.isFull()) {
            return;
        }

        // Camada 5: dias vizinhos completos no mesmo binary bucket
        visitNeighborDaysAllHoursAllTx(queryVector, top5, visited, binaryBucket, dayBucket, 1);
    }

    private void visitBucket(QueryVector queryVector, Top5Selector top5, boolean[] visited, int bucketKey) {
        if (bucketKey < 0 || bucketKey >= Constants.BUCKET_COUNT) {
            return;
        }
        if (visited[bucketKey]) {
            return;
        }
        visited[bucketKey] = true;

        int start = bucketStarts[bucketKey];
        int end = bucketStarts[bucketKey + 1];
        for (int row = start; row < end; row++) {
            scanRow(queryVector, top5, row);
        }
    }

    private void scanRow(QueryVector queryVector, Top5Selector top5, int row) {
        int offset = row * Constants.VECTOR_DIMENSIONS;
        long dist = DistanceKernel.squaredDistanceEarlyAbort(
            queryVector.values,
            vectors,
            offset,
            top5.currentWorst()
        );
        if (dist <= top5.currentWorst()) {
            int origId = origIds.length == labels.length ? origIds[row] : row;
            top5.offer(dist, labels[row], origId);
        }
    }

    private void visitHourDayTxWindow(
        QueryVector queryVector,
        Top5Selector top5,
        boolean[] visited,
        int binaryBucket,
        int hourBucket,
        int dayBucket,
        int txBucket,
        int hourRadius,
        int dayRadius,
        int txRadius,
        int skipKey
    ) {
        int hourStart = Math.max(0, hourBucket - hourRadius);
        int hourEnd = Math.min(Constants.HOURS - 1, hourBucket + hourRadius);

        int dayStart = Math.max(0, dayBucket - dayRadius);
        int dayEnd = Math.min(Constants.DAYS - 1, dayBucket + dayRadius);

        int txStart = Math.max(0, txBucket - txRadius);
        int txEnd = Math.min(Constants.TX_BUCKETS - 1, txBucket + txRadius);

        for (int h = hourStart; h <= hourEnd; h++) {
            for (int d = dayStart; d <= dayEnd; d++) {
                for (int t = txStart; t <= txEnd; t++) {
                    int key = RuntimeBucketKeyEncoder.encode(binaryBucket, h, d, t);
                    if (key == skipKey) {
                        continue;
                    }
                    visitBucket(queryVector, top5, visited, key);
                }
            }
        }
    }

    private void visitSameDayHourWindowAllTx(
        QueryVector queryVector,
        Top5Selector top5,
        boolean[] visited,
        int binaryBucket,
        int hourBucket,
        int dayBucket,
        int hourRadius
    ) {
        int hourStart = Math.max(0, hourBucket - hourRadius);
        int hourEnd = Math.min(Constants.HOURS - 1, hourBucket + hourRadius);

        for (int h = hourStart; h <= hourEnd; h++) {
            for (int t = 0; t < Constants.TX_BUCKETS; t++) {
                int key = RuntimeBucketKeyEncoder.encode(binaryBucket, h, dayBucket, t);
                visitBucket(queryVector, top5, visited, key);
            }
        }
    }

    private void visitSameHourDayWindowTxWindow(
        QueryVector queryVector,
        Top5Selector top5,
        boolean[] visited,
        int binaryBucket,
        int hourBucket,
        int dayBucket,
        int txBucket,
        int dayRadius,
        int txRadius
    ) {
        int dayStart = Math.max(0, dayBucket - dayRadius);
        int dayEnd = Math.min(Constants.DAYS - 1, dayBucket + dayRadius);

        int txStart = Math.max(0, txBucket - txRadius);
        int txEnd = Math.min(Constants.TX_BUCKETS - 1, txBucket + txRadius);

        for (int d = dayStart; d <= dayEnd; d++) {
            for (int t = txStart; t <= txEnd; t++) {
                int key = RuntimeBucketKeyEncoder.encode(binaryBucket, hourBucket, d, t);
                visitBucket(queryVector, top5, visited, key);
            }
        }
    }

    private void visitSameDayAllHoursAllTx(
        QueryVector queryVector,
        Top5Selector top5,
        boolean[] visited,
        int binaryBucket,
        int dayBucket
    ) {
        for (int h = 0; h < Constants.HOURS; h++) {
            for (int t = 0; t < Constants.TX_BUCKETS; t++) {
                int key = RuntimeBucketKeyEncoder.encode(binaryBucket, h, dayBucket, t);
                visitBucket(queryVector, top5, visited, key);
            }
        }
    }

    private void visitNeighborDaysAllHoursAllTx(
        QueryVector queryVector,
        Top5Selector top5,
        boolean[] visited,
        int binaryBucket,
        int dayBucket,
        int dayRadius
    ) {
        int dayStart = Math.max(0, dayBucket - dayRadius);
        int dayEnd = Math.min(Constants.DAYS - 1, dayBucket + dayRadius);

        for (int d = dayStart; d <= dayEnd; d++) {
            if (d == dayBucket) {
                continue;
            }
            for (int h = 0; h < Constants.HOURS; h++) {
                for (int t = 0; t < Constants.TX_BUCKETS; t++) {
                    int key = RuntimeBucketKeyEncoder.encode(binaryBucket, h, d, t);
                    visitBucket(queryVector, top5, visited, key);
                }
            }
        }
    }
}
