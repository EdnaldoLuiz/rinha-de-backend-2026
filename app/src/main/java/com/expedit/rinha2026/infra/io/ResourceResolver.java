package com.expedit.rinha2026.infra.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceResolver {
    public String readUtf8(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location is required");
        }

        if (location.startsWith("classpath:")) {
            String resource = location.substring("classpath:".length());
            if (resource.startsWith("/")) {
                resource = resource.substring(1);
            }

            try (InputStream in = ResourceResolver.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException("Resource not found: " + location);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return Files.readString(Path.of(location), StandardCharsets.UTF_8);
    }
}
