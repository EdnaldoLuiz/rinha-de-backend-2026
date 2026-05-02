package com.expedit.rinha2026.tools.preprocessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public final class ReferenceJsonReader {
    public String read(Path input) throws IOException {
        if (input.toString().endsWith(".gz")) {
            try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(input))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return Files.readString(input, StandardCharsets.UTF_8);
    }
}
