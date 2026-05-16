package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.IvfIndex;
import java.util.Arrays;
import java.util.List;

public final class IvfIndexBuilder {
    public IvfIndex build(
        List<ReferenceRecord> records,
        int requestedClusters,
        int fastNprobe,
        int fullNprobe,
        int sampleSize,
        int iterations
    ) {
        if (records.isEmpty()) {
            return IvfIndex.empty();
        }

        int clusters = Math.max(1, Math.min(requestedClusters, records.size()));
        int effectiveFastNprobe = Math.max(1, Math.min(fastNprobe, clusters));
        int effectiveFullNprobe = Math.max(effectiveFastNprobe, Math.min(fullNprobe, clusters));

        KMeansTrainer trainer = new KMeansTrainer();
        float[] centroids = trainer.train(records, clusters, Math.max(clusters, sampleSize), iterations);

        int[] assignments = new int[records.size()];
        int[] counts = new int[clusters];
        for (int i = 0; i < records.size(); i++) {
            int cluster = trainer.nearestCluster(records.get(i).vector14(), centroids, clusters);
            assignments[i] = cluster;
            counts[cluster]++;
        }
        printClusterStats(counts);

        int[] offsets = new int[clusters + 1];
        for (int cluster = 0; cluster < clusters; cluster++) {
            offsets[cluster + 1] = offsets[cluster] + counts[cluster];
        }

        short[] vectors = new short[records.size() * Constants.VECTOR_DIMENSIONS];
        byte[] labels = new byte[records.size()];
        int[] origIds = new int[records.size()];
        short[] bboxMin = new short[clusters * Constants.VECTOR_DIMENSIONS];
        short[] bboxMax = new short[clusters * Constants.VECTOR_DIMENSIONS];
        Arrays.fill(bboxMin, Short.MAX_VALUE);
        Arrays.fill(bboxMax, Short.MIN_VALUE);

        int[] positions = offsets.clone();
        for (int i = 0; i < records.size(); i++) {
            ReferenceRecord record = records.get(i);
            int cluster = assignments[i];
            int row = positions[cluster]++;
            int offset = row * Constants.VECTOR_DIMENSIONS;
            int bboxOffset = cluster * Constants.VECTOR_DIMENSIONS;

            float[] vector = record.vector14();
            for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
                short value = quantize(vector[dim]);
                vectors[offset + dim] = value;
                if (value < bboxMin[bboxOffset + dim]) {
                    bboxMin[bboxOffset + dim] = value;
                }
                if (value > bboxMax[bboxOffset + dim]) {
                    bboxMax[bboxOffset + dim] = value;
                }
            }
            labels[row] = record.label();
            origIds[row] = i;
        }

        for (int cluster = 0; cluster < clusters; cluster++) {
            if (counts[cluster] == 0) {
                int bboxOffset = cluster * Constants.VECTOR_DIMENSIONS;
                Arrays.fill(bboxMin, bboxOffset, bboxOffset + Constants.VECTOR_DIMENSIONS, (short) 0);
                Arrays.fill(bboxMax, bboxOffset, bboxOffset + Constants.VECTOR_DIMENSIONS, (short) 0);
            }
        }

        return new IvfIndex(
            Constants.VECTOR_DIMENSIONS,
            clusters,
            effectiveFastNprobe,
            effectiveFullNprobe,
            centroids,
            bboxMin,
            bboxMax,
            offsets,
            vectors,
            labels,
            origIds
        );
    }

    private short quantize(float value) {
        int scaled = Math.round(value * Constants.VECTOR_SCALE);
        if (scaled > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (scaled < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) scaled;
    }

    private void printClusterStats(int[] counts) {
        int[] sorted = counts.clone();
        Arrays.sort(sorted);

        int empty = 0;
        for (int count : sorted) {
            if (count == 0) {
                empty++;
            }
        }

        int p50 = percentile(sorted, 0.50);
        int p90 = percentile(sorted, 0.90);
        int p95 = percentile(sorted, 0.95);
        int p99 = percentile(sorted, 0.99);
        int max = sorted[sorted.length - 1];

        System.out.printf(
            "IVF cluster stats: clusters=%d empty=%d p50=%d p90=%d p95=%d p99=%d max=%d%n",
            sorted.length,
            empty,
            p50,
            p90,
            p95,
            p99,
            max
        );
    }

    private int percentile(int[] sorted, double ratio) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.floor((sorted.length - 1) * ratio);
        if (idx < 0) {
            idx = 0;
        } else if (idx >= sorted.length) {
            idx = sorted.length - 1;
        }
        return sorted[idx];
    }
}
