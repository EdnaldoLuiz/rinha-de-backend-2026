package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IvfIndexLoader {
    public IvfIndex load(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int magic = in.readInt();
            int version = in.readInt();
            int dimensions = in.readInt();
            int vectorCount = in.readInt();
            int clusters = in.readInt();
            int fastNprobe = in.readInt();
            int fullNprobe = in.readInt();

            if (magic != IvfIndexFormat.MAGIC) {
                throw new IOException("Invalid IVF index magic: " + magic);
            }
            if (version != IvfIndexFormat.VERSION) {
                throw new IOException("Unsupported IVF index version: " + version);
            }
            if (dimensions != Constants.VECTOR_DIMENSIONS) {
                throw new IOException("Unsupported IVF dimensions: " + dimensions);
            }
            if (clusters < 0 || fastNprobe < 0 || fullNprobe < fastNprobe) {
                throw new IOException("Invalid IVF probing metadata");
            }

            float[] centroids = new float[dimensions * clusters];
            for (int i = 0; i < centroids.length; i++) {
                centroids[i] = in.readFloat();
            }

            short[] bboxMin = new short[dimensions * clusters];
            for (int i = 0; i < bboxMin.length; i++) {
                bboxMin[i] = in.readShort();
            }

            short[] bboxMax = new short[dimensions * clusters];
            for (int i = 0; i < bboxMax.length; i++) {
                bboxMax[i] = in.readShort();
            }

            int[] offsets = new int[clusters + 1];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = in.readInt();
            }
            if (offsets.length > 0 && offsets[offsets.length - 1] != vectorCount) {
                throw new IOException("Invalid IVF offsets: last offset does not match vector count");
            }

            short[] vectors = new short[vectorCount * dimensions];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = in.readShort();
            }

            byte[] labels = new byte[vectorCount];
            in.readFully(labels);
            int[] origIds = new int[vectorCount];
            for (int i = 0; i < origIds.length; i++) {
                origIds[i] = in.readInt();
            }

            return new IvfIndex(
                dimensions,
                clusters,
                Math.min(fastNprobe, clusters),
                Math.min(fullNprobe, clusters),
                centroids,
                bboxMin,
                bboxMax,
                offsets,
                vectors,
                labels,
                origIds
            );
        }
    }
}
