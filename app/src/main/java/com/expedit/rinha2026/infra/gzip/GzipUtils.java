package com.expedit.rinha2026.infra.gzip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public final class GzipUtils {
    private GzipUtils() {
    }

    public static String readUtf8(Path path) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
