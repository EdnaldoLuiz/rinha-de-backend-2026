package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.infra.index.BucketMetadata;
import java.util.Arrays;

public final class ExactKnnSearchEngine implements SearchEngine {
    private static final int HOURS = 24;
    private static final int TX_BUCKETS = 4;
    private static final int PRIORITY_BUCKET_CAPACITY = 10;

    private final float[] vectors;
    private final byte[] labels;
    private final BucketMetadata[] buckets;
    private final ThreadLocal<Top5Selector> top5Local;
    private final int[] bucketIndexByKey;
    private final ThreadLocal<int[]> priorityBucketsLocal;

    public ExactKnnSearchEngine(float[] vectors, byte[] labels) {
        this(vectors, labels, new BucketMetadata[0]);
    }

    public ExactKnnSearchEngine(float[] vectors, byte[] labels, BucketMetadata[] buckets) {
        this.vectors = vectors;
        this.labels = labels;
        this.buckets = buckets == null ? new BucketMetadata[0] : buckets;
        this.top5Local = ThreadLocal.withInitial(Top5Selector::new);
        this.bucketIndexByKey = buildBucketIndexByKey(this.buckets);
        this.priorityBucketsLocal = ThreadLocal.withInitial(() -> new int[PRIORITY_BUCKET_CAPACITY]);
    }

    public static ExactKnnSearchEngine empty() {
        return new ExactKnnSearchEngine(new float[0], new byte[0], new BucketMetadata[0]);
    }

    @Override
    public SearchResult search(QueryVector queryVector) {
        SearchResult result = new SearchResult();
        if (labels.length == 0) {
            result.fraudCount = 0;
            result.worstDistance = Float.POSITIVE_INFINITY;
            return result;
        }

        Top5Selector top5 = top5Local.get();
        top5.reset();

        if (buckets.length == 0) {
            scanAll(queryVector, top5);
        } else {
            scanNeighborhood(queryVector, top5);
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
        float[] q = queryVector.values;

        int binaryBucket = RuntimeBucketKeyEncoder.binaryBucket(q[9], q[10], q[11]);
        int hourBucket = RuntimeBucketKeyEncoder.hourBucket(q[3]);
        int dayBucket = RuntimeBucketKeyEncoder.dayBucket(q[4]);
        int txBucket = RuntimeBucketKeyEncoder.txBucket(q[8]);

        int[] priorityBucketIndexes = priorityBucketsLocal.get();
        int priorityCount = 0;

        // 1) bucket exato primeiro
        priorityCount = addPriorityBucketIndex(
            priorityBucketIndexes,
            priorityCount,
            RuntimeBucketKeyEncoder.encode(binaryBucket, hourBucket, dayBucket, txBucket)
        );

        // 2) vizinhança de hour ±1 e tx ±1 no mesmo day
        int hourStart = Math.max(0, hourBucket - 1);
        int hourEnd = Math.min(HOURS - 1, hourBucket + 1);

        int txStart = Math.max(0, txBucket - 1);
        int txEnd = Math.min(TX_BUCKETS - 1, txBucket + 1);

        for (int h = hourStart; h <= hourEnd; h++) {
            for (int t = txStart; t <= txEnd; t++) {
                int key = RuntimeBucketKeyEncoder.encode(binaryBucket, h, dayBucket, t);
                priorityCount = addPriorityBucketIndex(priorityBucketIndexes, priorityCount, key);
            }
        }

        // 3) escaneia primeiro buckets mais prováveis de conter vizinhos próximos
        for (int i = 0; i < priorityCount; i++) {
            scanBucketByIndex(queryVector, top5, priorityBucketIndexes[i]);
        }

        // 4) mantém exatidão: varre restantes sem lookup por chave e sem alocação por consulta
        for (int bucketIndex = 0; bucketIndex < buckets.length; bucketIndex++) {
            if (containsPriorityIndex(priorityBucketIndexes, priorityCount, bucketIndex)) {
                continue;
            }
            scanBucketByIndex(queryVector, top5, bucketIndex);
        }
    }

    private int addPriorityBucketIndex(int[] priorityBucketIndexes, int priorityCount, int key) {
        if (key < 0 || key >= bucketIndexByKey.length) {
            return priorityCount;
        }

        int bucketIndex = bucketIndexByKey[key];
        if (bucketIndex < 0) {
            return priorityCount;
        }

        for (int i = 0; i < priorityCount; i++) {
            if (priorityBucketIndexes[i] == bucketIndex) {
                return priorityCount;
            }
        }

        priorityBucketIndexes[priorityCount] = bucketIndex;
        return priorityCount + 1;
    }

    private boolean containsPriorityIndex(int[] priorityBucketIndexes, int priorityCount, int bucketIndex) {
        for (int i = 0; i < priorityCount; i++) {
            if (priorityBucketIndexes[i] == bucketIndex) {
                return true;
            }
        }
        return false;
    }

    private void scanBucketByIndex(QueryVector queryVector, Top5Selector top5, int bucketIndex) {
        BucketMetadata bucket = buckets[bucketIndex];
        if (bucket.count() <= 0) {
            return;
        }

        int start = bucket.startVector();
        int end = start + bucket.count();

        for (int row = start; row < end; row++) {
            scanRow(queryVector, top5, row);
        }
    }

    private void scanRow(QueryVector queryVector, Top5Selector top5, int row) {
        int offset = row * Constants.VECTOR_STRIDE;
        float dist = DistanceKernel.squaredDistanceEarlyAbort(
            queryVector.values,
            vectors,
            offset,
            top5.currentWorst()
        );
        if (dist < top5.currentWorst()) {
            top5.offer(dist, labels[row]);
        }
    }

    private static int[] buildBucketIndexByKey(BucketMetadata[] buckets) {
        int maxKey = 0;
        for (BucketMetadata bucket : buckets) {
            if (bucket.key() > maxKey) {
                maxKey = bucket.key();
            }
        }

        int[] indexByKey = new int[maxKey + 1];
        Arrays.fill(indexByKey, -1);

        for (int i = 0; i < buckets.length; i++) {
            indexByKey[buckets[i].key()] = i;
        }

        return indexByKey;
    }
}
