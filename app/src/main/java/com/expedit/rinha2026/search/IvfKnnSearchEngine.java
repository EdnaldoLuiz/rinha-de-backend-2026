package com.expedit.rinha2026.search;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.domain.SearchResult;
import com.expedit.rinha2026.infra.index.IvfIndex;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class IvfKnnSearchEngine implements SearchEngine {
    private static final boolean STATS = "1".equals(System.getenv().getOrDefault("IVF_STATS", "0"));
    private static final boolean BBOX_REPAIR = "1".equals(System.getenv().getOrDefault("IVF_BBOX_REPAIR", "0"));
    private static final boolean TOP5_DEBUG =
        !System.getenv().getOrDefault("RUNTIME_DEBUG_TX_IDS", "").isBlank();
    private static final float INV_VECTOR_SCALE = 1.0f / Constants.VECTOR_SCALE;

    private final IvfIndex index;
    private final ThreadLocal<Top5Selector> top5Local;
    private final ThreadLocal<int[]> clusterLocal;
    private final ThreadLocal<float[]> distanceLocal;
    private final ThreadLocal<boolean[]> visitedClusterLocal;
    private final ThreadLocal<float[]> queryFloatLocal;
    private final ThreadLocal<float[]> clusterDistanceScratchLocal;
    private final ThreadLocal<DebugTop5Snapshot> debugSnapshotLocal;
    private final LongAdder requests;
    private final LongAdder fullProbeRequests;
    private final LongAdder scannedRows;
    private final AtomicLong maxScannedRows;

    public IvfKnnSearchEngine(IvfIndex index) {
        this.index = index;
        this.top5Local = ThreadLocal.withInitial(Top5Selector::new);
        this.clusterLocal = ThreadLocal.withInitial(() -> new int[Math.max(1, index.fullNprobe())]);
        this.distanceLocal = ThreadLocal.withInitial(() -> new float[Math.max(1, index.fullNprobe())]);
        this.visitedClusterLocal = ThreadLocal.withInitial(() -> new boolean[Math.max(1, index.clusters())]);
        this.queryFloatLocal = ThreadLocal.withInitial(() -> new float[Constants.VECTOR_DIMENSIONS]);
        this.clusterDistanceScratchLocal = ThreadLocal.withInitial(() -> new float[Math.max(1, index.clusters())]);
        this.debugSnapshotLocal = ThreadLocal.withInitial(DebugTop5Snapshot::new);
        this.requests = new LongAdder();
        this.fullProbeRequests = new LongAdder();
        this.scannedRows = new LongAdder();
        this.maxScannedRows = new AtomicLong();
    }

    public static IvfKnnSearchEngine empty() {
        return new IvfKnnSearchEngine(IvfIndex.empty());
    }

    @Override
    public SearchResult search(QueryVector queryVector) {
        SearchResult result = new SearchResult();
        if (index.vectorCount() == 0 || index.clusters() == 0) {
            result.fraudCount = 0;
            result.worstDistance = Long.MAX_VALUE;
            return result;
        }

        Top5Selector top5 = top5Local.get();
        top5.reset();

        int probeCount = Math.min(index.fullNprobe(), index.clusters());
        int[] clusters = clusterLocal.get();
        float[] distances = distanceLocal.get();
        boolean[] visitedClusters = BBOX_REPAIR ? visitedClusterLocal.get() : null;
        if (BBOX_REPAIR) {
            Arrays.fill(visitedClusters, false);
        }
        float[] qFloat = queryFloatLocal.get();
        float[] clusterDistanceScratch = clusterDistanceScratchLocal.get();
        prepareQueryFloat(queryVector.values, qFloat);
        selectTopClusters(qFloat, probeCount, clusters, distances, clusterDistanceScratch);

        int fastLimit = Math.min(index.fastNprobe(), probeCount);
        int scanned = scanClusters(queryVector, top5, clusters, 0, fastLimit, visitedClusters);

        int fastFrauds = top5.fraudCount();
        boolean useFull = !top5.isFull() || fastFrauds == 2 || fastFrauds == 3;
        if (useFull) {
            scanned += scanClusters(queryVector, top5, clusters, fastLimit, probeCount, visitedClusters);
            int fullFrauds = top5.fraudCount();
            if (BBOX_REPAIR && (fullFrauds == 2 || fullFrauds == 3)) {
                scanned += bboxRepair(queryVector, top5, visitedClusters);
            }
        }

        if (STATS) {
            requests.increment();
            scannedRows.add(scanned);
            if (useFull) {
                fullProbeRequests.increment();
            }
            maxScannedRows.accumulateAndGet(scanned, Math::max);

            long req = requests.sum();
            if ((req % 5_000) == 0) {
                long totalRows = scannedRows.sum();
                long full = fullProbeRequests.sum();
                long maxRows = maxScannedRows.get();
                System.out.printf(
                    "IVF stats: requests=%d avgRows=%.2f fullProbeRate=%.2f%% maxRows=%d%n",
                    req,
                    totalRows / (double) req,
                    (full * 100.0) / req,
                    maxRows
                );
            }
        }

        result.fraudCount = top5.fraudCount();
        result.worstDistance = top5.currentWorst();
        if (TOP5_DEBUG) {
            DebugTop5Snapshot snapshot = debugSnapshotLocal.get();
            top5.copyState(snapshot.distances, snapshot.labels, snapshot.ids);
        }
        return result;
    }

    public DebugTop5Snapshot currentThreadTop5Snapshot() {
        DebugTop5Snapshot src = debugSnapshotLocal.get();
        DebugTop5Snapshot copy = new DebugTop5Snapshot();
        System.arraycopy(src.distances, 0, copy.distances, 0, src.distances.length);
        System.arraycopy(src.labels, 0, copy.labels, 0, src.labels.length);
        System.arraycopy(src.ids, 0, copy.ids, 0, src.ids.length);
        return copy;
    }

    private void prepareQueryFloat(short[] query, float[] qFloat) {
        qFloat[0] = query[0] * INV_VECTOR_SCALE;
        qFloat[1] = query[1] * INV_VECTOR_SCALE;
        qFloat[2] = query[2] * INV_VECTOR_SCALE;
        qFloat[3] = query[3] * INV_VECTOR_SCALE;
        qFloat[4] = query[4] * INV_VECTOR_SCALE;
        qFloat[5] = query[5] * INV_VECTOR_SCALE;
        qFloat[6] = query[6] * INV_VECTOR_SCALE;
        qFloat[7] = query[7] * INV_VECTOR_SCALE;
        qFloat[8] = query[8] * INV_VECTOR_SCALE;
        qFloat[9] = query[9] * INV_VECTOR_SCALE;
        qFloat[10] = query[10] * INV_VECTOR_SCALE;
        qFloat[11] = query[11] * INV_VECTOR_SCALE;
        qFloat[12] = query[12] * INV_VECTOR_SCALE;
        qFloat[13] = query[13] * INV_VECTOR_SCALE;
    }

    private void selectTopClusters(
        float[] qFloat,
        int probeCount,
        int[] selectedClusters,
        float[] selectedDistances,
        float[] clusterDistances
    ) {
        int clusters = index.clusters();
        float[] centroids = index.centroids();

        Arrays.fill(selectedClusters, 0, probeCount, -1);
        Arrays.fill(selectedDistances, 0, probeCount, Float.POSITIVE_INFINITY);

        // Initialize with dim 0
        int base0 = 0;
        float q0 = qFloat[0];
        for (int cluster = 0; cluster < clusters; cluster++) {
            float delta = centroids[base0 + cluster] - q0;
            clusterDistances[cluster] = delta * delta;
        }

        // Accumulate dims 1-13
        for (int dim = 1; dim < Constants.VECTOR_DIMENSIONS; dim++) {
            int base = dim * clusters;
            float q = qFloat[dim];

            for (int cluster = 0; cluster < clusters; cluster++) {
                float delta = centroids[base + cluster] - q;
                clusterDistances[cluster] += delta * delta;
            }
        }

        for (int cluster = 0; cluster < clusters; cluster++) {
            float distance = clusterDistances[cluster];
            if (distance >= selectedDistances[probeCount - 1]) {
                continue;
            }

            int pos = probeCount - 1;
            while (pos > 0 && distance < selectedDistances[pos - 1]) {
                selectedDistances[pos] = selectedDistances[pos - 1];
                selectedClusters[pos] = selectedClusters[pos - 1];
                pos--;
            }
            selectedDistances[pos] = distance;
            selectedClusters[pos] = cluster;
        }
    }

    private int scanClusters(
        QueryVector queryVector,
        Top5Selector top5,
        int[] clusters,
        int start,
        int end,
        boolean[] visitedClusters
    ) {
        int scanned = 0;
        for (int i = start; i < end; i++) {
            int cluster = clusters[i];
            if (cluster >= 0) {
                if (visitedClusters != null) {
                    visitedClusters[cluster] = true;
                }
                scanned += scanCluster(queryVector, top5, cluster);
            }
        }
        return scanned;
    }

    private int bboxRepair(QueryVector queryVector, Top5Selector top5, boolean[] visitedClusters) {
        int scanned = 0;
        short[] query = queryVector.values;
        long worst = top5.currentWorst();
        for (int cluster = 0; cluster < index.clusters(); cluster++) {
            if (visitedClusters[cluster]) {
                continue;
            }
            if (bboxLowerBound(query, cluster) <= worst) {
                visitedClusters[cluster] = true;
                scanned += scanCluster(queryVector, top5, cluster);
                worst = top5.currentWorst();
            }
        }
        return scanned;
    }

    private long bboxLowerBound(short[] query, int cluster) {
        int base = cluster * Constants.VECTOR_DIMENSIONS;
        short[] bboxMin = index.bboxMin();
        short[] bboxMax = index.bboxMax();
        long sum = 0L;

        for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
            int q = query[dim];
            int min = bboxMin[base + dim];
            int max = bboxMax[base + dim];
            int delta = 0;
            if (q < min) {
                delta = min - q;
            } else if (q > max) {
                delta = q - max;
            }
            sum += (long) delta * delta;
        }
        return sum;
    }

    private int scanCluster(QueryVector queryVector, Top5Selector top5, int cluster) {
        int start = index.offsets()[cluster];
        int end = index.offsets()[cluster + 1];
        for (int row = start; row < end; row++) {
            scanRow(queryVector, top5, row);
        }
        return end - start;
    }

    private void scanRow(QueryVector queryVector, Top5Selector top5, int row) {
        int offset = row * Constants.VECTOR_DIMENSIONS;
        long dist = DistanceKernel.squaredDistanceEarlyAbort(
            queryVector.values,
            index.vectors(),
            offset,
            top5.currentWorst()
        );
        if (dist < top5.currentWorst()) {
            top5.offer(dist, index.labels()[row], index.origIds()[row]);
        }
    }

    public static final class DebugTop5Snapshot {
        private final long[] distances = new long[5];
        private final byte[] labels = new byte[5];
        private final int[] ids = new int[5];

        public long[] distances() {
            return distances;
        }

        public byte[] labels() {
            return labels;
        }

        public int[] ids() {
            return ids;
        }
    }
}
