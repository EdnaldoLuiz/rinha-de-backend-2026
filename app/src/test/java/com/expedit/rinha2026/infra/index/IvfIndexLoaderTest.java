package com.expedit.rinha2026.infra.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IvfIndexLoaderTest {
    @Test
    void shouldLoadIvfIndex() throws Exception {
        Path temp = Files.createTempFile("rinha-ivf-index", ".dat");

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(IvfIndexFormat.MAGIC);
            out.writeInt(IvfIndexFormat.VERSION);
            out.writeInt(Constants.VECTOR_DIMENSIONS);
            out.writeInt(2);
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(1);

            for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                out.writeFloat(0f);
            }
            for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                out.writeShort(0);
            }
            for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                out.writeShort(10_000);
            }

            out.writeInt(0);
            out.writeInt(2);

            short[] vectors = new short[2 * Constants.VECTOR_DIMENSIONS];
            vectors[0] = 1000;
            vectors[14] = 9000;
            for (short vector : vectors) {
                out.writeShort(vector);
            }
            out.write(new byte[] {0, 1});
            out.writeInt(10);
            out.writeInt(20);
        }

        IvfIndex loaded = new IvfIndexLoader().load(temp);

        assertEquals(Constants.VECTOR_DIMENSIONS, loaded.dimensions());
        assertEquals(1, loaded.clusters());
        assertEquals(2, loaded.vectorCount());
        assertEquals(2 * Constants.VECTOR_DIMENSIONS, loaded.vectors().length);
        assertArrayEquals(new byte[] {0, 1}, loaded.labels());
        assertArrayEquals(new int[] {10, 20}, loaded.origIds());
    }
}
