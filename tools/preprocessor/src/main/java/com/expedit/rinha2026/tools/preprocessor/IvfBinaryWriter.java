package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.infra.index.IvfIndex;
import com.expedit.rinha2026.infra.index.IvfIndexFormat;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IvfBinaryWriter {
    public void write(Path output, IvfIndex index) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            out.writeInt(IvfIndexFormat.MAGIC);
            out.writeInt(IvfIndexFormat.VERSION);
            out.writeInt(index.dimensions());
            out.writeInt(index.vectorCount());
            out.writeInt(index.clusters());
            out.writeInt(index.fastNprobe());
            out.writeInt(index.fullNprobe());

            for (float centroid : index.centroids()) {
                out.writeFloat(centroid);
            }
            for (short value : index.bboxMin()) {
                out.writeShort(value);
            }
            for (short value : index.bboxMax()) {
                out.writeShort(value);
            }
            for (int offset : index.offsets()) {
                out.writeInt(offset);
            }
            for (short vector : index.vectors()) {
                out.writeShort(vector);
            }
            out.write(index.labels());
            for (int origId : index.origIds()) {
                out.writeInt(origId);
            }
        }
    }
}
