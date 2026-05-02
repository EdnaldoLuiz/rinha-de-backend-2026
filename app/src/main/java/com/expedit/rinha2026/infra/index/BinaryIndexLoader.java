package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BinaryIndexLoader {
    public LoadedIndex load(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int magic = in.readInt();
            int version = in.readInt();
            int stride = in.readInt();
            int dimensions = in.readInt();
            int vectorCount = in.readInt();
            int bucketCount = in.readInt();

            if (magic != BinaryIndexFormat.MAGIC) {
                throw new IOException("Invalid index magic: " + magic);
            }
            if (version != BinaryIndexFormat.VERSION) {
                throw new IOException("Unsupported index version: " + version);
            }
            if (stride != Constants.VECTOR_STRIDE) {
                throw new IOException("Unsupported index stride: " + stride);
            }
            if (dimensions != Constants.VECTOR_DIMENSIONS) {
                throw new IOException("Unsupported dimensions: " + dimensions);
            }
            if (vectorCount < 0 || bucketCount < 0) {
                throw new IOException("Invalid counts: vectors=" + vectorCount + " buckets=" + bucketCount);
            }

            BucketMetadata[] buckets = new BucketMetadata[bucketCount];
            for (int b = 0; b < bucketCount; b++) {
                int key = in.readInt();
                int startVector = in.readInt();
                int count = in.readInt();
                float[] minBounds = new float[dimensions];
                float[] maxBounds = new float[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    minBounds[i] = in.readFloat();
                }
                for (int i = 0; i < dimensions; i++) {
                    maxBounds[i] = in.readFloat();
                }
                buckets[b] = new BucketMetadata(key, startVector, count, minBounds, maxBounds);
            }

            float[] vectors = new float[vectorCount * stride];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = in.readFloat();
            }

            byte[] labels = new byte[vectorCount];
            in.readFully(labels);

            return new LoadedIndex(dimensions, stride, vectors, labels, buckets);
        }
    }

    public LoadedIndex loadOrEmpty(Path path) {
        if (!Files.exists(path)) {
            return LoadedIndex.empty();
        }

        try {
            return load(path);
        } catch (IOException e) {
            return LoadedIndex.empty();
        }
    }
}
