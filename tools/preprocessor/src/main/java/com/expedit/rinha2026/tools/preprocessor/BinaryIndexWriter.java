package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.BinaryIndexFormat;
import com.expedit.rinha2026.infra.index.BucketMetadata;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BinaryIndexWriter {
    public void write(Path output, LoadedIndex index) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            int vectorCount = index.labels().length;
            int bucketCount = index.buckets().length;

            out.writeInt(BinaryIndexFormat.MAGIC);
            out.writeInt(BinaryIndexFormat.VERSION);
            out.writeInt(Constants.VECTOR_STRIDE);
            out.writeInt(Constants.VECTOR_DIMENSIONS);
            out.writeInt(vectorCount);
            out.writeInt(bucketCount);

            for (BucketMetadata bucket : index.buckets()) {
                out.writeInt(bucket.key());
                out.writeInt(bucket.startVector());
                out.writeInt(bucket.count());
                for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                    out.writeFloat(bucket.minBounds()[i]);
                }
                for (int i = 0; i < Constants.VECTOR_DIMENSIONS; i++) {
                    out.writeFloat(bucket.maxBounds()[i]);
                }
            }

            for (float v : index.vectors()) {
                out.writeFloat(v);
            }
            out.write(index.labels());
        }
    }
}
