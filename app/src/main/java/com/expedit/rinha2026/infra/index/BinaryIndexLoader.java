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
            int dimensions = in.readInt();
            int vectorCount = in.readInt();
            int bucketCount = in.readInt();

            if (magic != BinaryIndexFormat.MAGIC) {
                throw new IOException("Invalid index magic: " + magic);
            }
            if (version != BinaryIndexFormat.VERSION) {
                throw new IOException("Unsupported index version: " + version);
            }
            if (dimensions != Constants.VECTOR_DIMENSIONS) {
                throw new IOException("Unsupported dimensions: " + dimensions);
            }
            if (bucketCount != Constants.BUCKET_COUNT) {
                throw new IOException("Unsupported bucket count: " + bucketCount);
            }

            int[] bucketStarts = new int[bucketCount + 1];
            for (int i = 0; i < bucketStarts.length; i++) {
                bucketStarts[i] = in.readInt();
            }

            short[] vectors = new short[vectorCount * dimensions];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = in.readShort();
            }

            byte[] labels = new byte[vectorCount];
            in.readFully(labels);

            return new LoadedIndex(dimensions, vectors, labels, bucketStarts);
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
