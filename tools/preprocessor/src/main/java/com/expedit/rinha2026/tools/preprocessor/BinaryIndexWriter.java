package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.BinaryIndexFormat;
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
            out.writeInt(BinaryIndexFormat.MAGIC);
            out.writeInt(BinaryIndexFormat.VERSION);
            out.writeInt(index.dimensions());
            out.writeInt(index.vectorCount());
            out.writeInt(Constants.BUCKET_COUNT);

            for (int value : index.bucketStarts()) {
                out.writeInt(value);
            }

            for (short v : index.vectors()) {
                out.writeShort(v);
            }
            out.write(index.labels());
        }
    }
}
