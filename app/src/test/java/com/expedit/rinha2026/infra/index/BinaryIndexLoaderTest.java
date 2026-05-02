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
            out.writeInt(Constants.VECTOR_STRIDE);
            out.writeInt(Constants.VECTOR_DIMENSIONS);
            out.writeInt(2);
            out.writeInt(1);

            out.writeInt(7); // bucket key
            out.writeInt(0); // start
            out.writeInt(2); // count
            for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                out.writeFloat(0f);
            }
            for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                out.writeFloat(1f);
            }

            float[] vectors = new float[2 * Constants.VECTOR_STRIDE];
            vectors[0] = 0.1f;
            vectors[16] = 0.9f;
            for (float v : vectors) {
                out.writeFloat(v);
            }

            out.write(new byte[] {0, 1});
        }

        LoadedIndex loaded = new BinaryIndexLoader().load(temp);
        assertEquals(Constants.VECTOR_DIMENSIONS, loaded.dimensions());
        assertEquals(Constants.VECTOR_STRIDE, loaded.stride());
        assertEquals(1, loaded.buckets().length);
        assertEquals(32, loaded.vectors().length);
        assertEquals(2, loaded.labels().length);
        assertArrayEquals(new byte[] {0, 1}, loaded.labels());
    }
}
