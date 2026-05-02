package com.expedit.rinha2026.infra.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BinaryIndexLoaderTest {
    @Test
    void shouldLoadBinaryIndex() throws Exception {
        Path temp = Files.createTempFile("rinha-index", ".dat");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(BinaryIndexFormat.MAGIC);
            out.writeInt(BinaryIndexFormat.VERSION);
            out.writeInt(Constants.VECTOR_DIMENSIONS);
            out.writeInt(2);
            out.writeInt(Constants.BUCKET_COUNT);

            int[] bucketStarts = new int[Constants.BUCKET_COUNT + 1];
            bucketStarts[0] = 0;
            bucketStarts[1] = 2;
            for (int i = 2; i < bucketStarts.length; i++) {
                bucketStarts[i] = 2;
            }

            for (int value : bucketStarts) {
                out.writeInt(value);
            }

            short[] vectors = new short[2 * Constants.VECTOR_DIMENSIONS];
            vectors[0] = 1000;
            vectors[14] = 9000;
            for (short v : vectors) {
                out.writeShort(v);
            }

            out.write(new byte[] {0, 1});
        }

        LoadedIndex loaded = new BinaryIndexLoader().load(temp);
        assertEquals(Constants.VECTOR_DIMENSIONS, loaded.dimensions());
        assertEquals(2, loaded.vectorCount());
        assertEquals(Constants.BUCKET_COUNT + 1, loaded.bucketStarts().length);
        assertEquals(28, loaded.vectors().length);
        assertArrayEquals(new byte[] {0, 1}, loaded.labels());
    }
}
