package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import java.util.Arrays;
import java.util.List;

public final class KMeansTrainer {
    public float[] train(List<ReferenceRecord> records, int clusters, int sampleSize, int iterations) {
        int sampleCount = Math.min(sampleSize, records.size());
        int[] sampleIndexes = sampleIndexes(records.size(), sampleCount);
        float[] centroids = initialCentroids(records, sampleIndexes, clusters);

        float[] sums = new float[Constants.VECTOR_DIMENSIONS * clusters];
        int[] counts = new int[clusters];

        for (int iteration = 0; iteration < iterations; iteration++) {
            Arrays.fill(sums, 0f);
            Arrays.fill(counts, 0);

            for (int sampleIndex : sampleIndexes) {
                float[] vector = records.get(sampleIndex).vector14();
                int cluster = nearestCluster(vector, centroids, clusters);
                counts[cluster]++;
                for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
                    sums[(dim * clusters) + cluster] += vector[dim];
                }
            }

            for (int cluster = 0; cluster < clusters; cluster++) {
                if (counts[cluster] == 0) {
                    copyVector(records.get(sampleIndexes[(cluster + iteration) % sampleCount]).vector14(), centroids, clusters, cluster);
                    continue;
                }
                for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
                    centroids[(dim * clusters) + cluster] = sums[(dim * clusters) + cluster] / counts[cluster];
                }
            }
        }

        return centroids;
    }

    public int nearestCluster(float[] vector, float[] centroids, int clusters) {
        int bestCluster = 0;
        float bestDistance = Float.POSITIVE_INFINITY;

        for (int cluster = 0; cluster < clusters; cluster++) {
            float distance = 0f;
            for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
                float delta = vector[dim] - centroids[(dim * clusters) + cluster];
                distance += delta * delta;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCluster = cluster;
            }
        }

        return bestCluster;
    }

    private int[] sampleIndexes(int recordCount, int sampleCount) {
        int[] indexes = new int[sampleCount];
        if (sampleCount == recordCount) {
            for (int i = 0; i < sampleCount; i++) {
                indexes[i] = i;
            }
            return indexes;
        }

        for (int i = 0; i < sampleCount; i++) {
            indexes[i] = (int) (((long) i * recordCount) / sampleCount);
        }
        return indexes;
    }

    private float[] initialCentroids(List<ReferenceRecord> records, int[] sampleIndexes, int clusters) {
        float[] centroids = new float[Constants.VECTOR_DIMENSIONS * clusters];
        for (int cluster = 0; cluster < clusters; cluster++) {
            int samplePosition = (int) (((long) cluster * sampleIndexes.length) / clusters);
            copyVector(records.get(sampleIndexes[samplePosition]).vector14(), centroids, clusters, cluster);
        }
        return centroids;
    }

    private void copyVector(float[] vector, float[] centroids, int clusters, int cluster) {
        for (int dim = 0; dim < Constants.VECTOR_DIMENSIONS; dim++) {
            centroids[(dim * clusters) + cluster] = vector[dim];
        }
    }
}
