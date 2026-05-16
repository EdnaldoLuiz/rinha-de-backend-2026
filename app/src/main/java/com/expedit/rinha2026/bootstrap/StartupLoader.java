package com.expedit.rinha2026.bootstrap;

import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.fallback.FallbackScorer;
import com.expedit.rinha2026.http.HttpRequestContext;
import com.expedit.rinha2026.http.NettyHttpServerBootstrap;
import com.expedit.rinha2026.infra.config.MccRiskTableLoader;
import com.expedit.rinha2026.infra.config.NormalizationConfigLoader;
import com.expedit.rinha2026.infra.index.BinaryIndexLoader;
import com.expedit.rinha2026.infra.index.IvfIndex;
import com.expedit.rinha2026.infra.index.IvfIndexLoader;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import com.expedit.rinha2026.infra.io.ResourceResolver;
import com.expedit.rinha2026.parser.BytePayloadParser;
import com.expedit.rinha2026.response.ResponseBuffers;
import com.expedit.rinha2026.response.ResponseWriter;
import com.expedit.rinha2026.search.AggressiveBucketSearchEngine;
import com.expedit.rinha2026.search.ExactKnnSearchEngine;
import com.expedit.rinha2026.search.IvfKnnSearchEngine;
import com.expedit.rinha2026.search.SearchEngine;
import com.expedit.rinha2026.search.SearchWarmup;
import com.expedit.rinha2026.vectorizer.FraudVectorizer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class StartupLoader {
    private static final String BINARY_SEARCH_MODE = System.getenv().getOrDefault("BINARY_SEARCH_MODE", "aggressive");
    private static final int SEARCH_WARMUP_ITERATIONS = intSetting("SEARCH_WARMUP_ITERATIONS", 1_000);

    public void start(AppConfig appConfig, ReadinessState readinessState) throws Exception {
        ResourceResolver resourceResolver = new ResourceResolver();

        NormalizationConfig normalizationConfig = new NormalizationConfigLoader()
            .load(resourceResolver.readUtf8(appConfig.normalizationLocation()));

        MccRiskTable mccRiskTable = new MccRiskTableLoader()
            .load(resourceResolver.readUtf8(appConfig.mccRiskLocation()));

        LoadedSearch loadedSearch = loadSearch(appConfig);

        HttpRequestContext context = new HttpRequestContext(
            new BytePayloadParser(),
            new FraudVectorizer(normalizationConfig, mccRiskTable),
            loadedSearch.searchEngine(),
            new ResponseBuffers(),
            new ResponseWriter(),
            new FallbackScorer()
        );

        System.out.printf(
            "Loaded index: type=%s path=%s hash=%s clusters=%d fastNprobe=%d fullNprobe=%d bboxRepair=%s vectors=%d%n",
            loadedSearch.indexType(),
            loadedSearch.indexPath(),
            loadedSearch.indexHash(),
            loadedSearch.clusters(),
            loadedSearch.fastNprobe(),
            loadedSearch.fullNprobe(),
            System.getenv().getOrDefault("IVF_BBOX_REPAIR", "0"),
            loadedSearch.vectorCount()
        );

        loadedSearch.warmup().run();

        new NettyHttpServerBootstrap().start(appConfig, readinessState, context);
        readinessState.setReady(true);

        System.out.printf("Startup ready on port %d with %d reference vectors.%n",
            appConfig.port(),
            loadedSearch.vectorCount());
    }

    private LoadedSearch loadSearch(AppConfig appConfig) throws Exception {
        if (appConfig.binaryIndexLocation() != null && !appConfig.binaryIndexLocation().isBlank()) {
            Path path = Path.of(appConfig.binaryIndexLocation());
            String hash = sha256Hex(path);
            try {
                IvfIndex index = new IvfIndexLoader().load(path);
                validateIvfRuntimeConfig(index);
                SearchEngine searchEngine = new IvfKnnSearchEngine(index);
                return new LoadedSearch(
                    "ivf",
                    path.toString(),
                    hash,
                    searchEngine,
                    index.clusters(),
                    index.fastNprobe(),
                    index.fullNprobe(),
                    index.vectorCount(),
                    () -> new SearchWarmup().warmIvf(index, searchEngine, SEARCH_WARMUP_ITERATIONS)
                );
            } catch (IOException ignored) {
                LoadedIndex index = new BinaryIndexLoader().load(path);
                validateBinaryRuntimeConfig();
                SearchEngine binarySearch = "exact".equalsIgnoreCase(BINARY_SEARCH_MODE)
                    ? new ExactKnnSearchEngine(index.vectors(), index.labels(), index.bucketStarts())
                    : new AggressiveBucketSearchEngine(index.vectors(), index.labels(), index.bucketStarts());
                return new LoadedSearch(
                    "bucket",
                    path.toString(),
                    hash,
                    binarySearch,
                    0,
                    0,
                    0,
                    index.vectorCount(),
                    () -> new SearchWarmup().warmLoadedIndex(index, binarySearch, SEARCH_WARMUP_ITERATIONS)
                );
            }
        }

        throw new IllegalStateException("Binary index is required");
    }

    private static int intSetting(String name, int fallback) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(name);
        }
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(raw);
    }

    private static void validateIvfRuntimeConfig(IvfIndex index) {
        String indexMode = System.getenv().getOrDefault("INDEX_MODE", "").trim();
        if (!indexMode.isBlank() && !"ivf".equalsIgnoreCase(indexMode)) {
            throw new IllegalStateException("INDEX_MODE=" + indexMode + " but IVF index was loaded");
        }

        int expectedClusters = intSetting("IVF_CLUSTERS", index.clusters());
        int expectedFast = intSetting("IVF_FAST_NPROBE", index.fastNprobe());
        int expectedFull = intSetting("IVF_FULL_NPROBE", index.fullNprobe());

        if (index.clusters() != expectedClusters) {
            throw new IllegalStateException(
                "IVF cluster mismatch: env=" + expectedClusters + " index=" + index.clusters());
        }
        if (index.fastNprobe() != expectedFast) {
            throw new IllegalStateException(
                "IVF fast nprobe mismatch: env=" + expectedFast + " index=" + index.fastNprobe());
        }
        if (index.fullNprobe() != expectedFull) {
            throw new IllegalStateException(
                "IVF full nprobe mismatch: env=" + expectedFull + " index=" + index.fullNprobe());
        }
    }

    private static void validateBinaryRuntimeConfig() {
        String indexMode = System.getenv().getOrDefault("INDEX_MODE", "").trim();
        if ("ivf".equalsIgnoreCase(indexMode)) {
            throw new IllegalStateException("INDEX_MODE=ivf but binary bucket index was loaded");
        }
    }

    private static String sha256Hex(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream in = java.nio.file.Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute SHA-256 for " + path, e);
        }
    }

    private record LoadedSearch(
        String indexType,
        String indexPath,
        String indexHash,
        SearchEngine searchEngine,
        int clusters,
        int fastNprobe,
        int fullNprobe,
        int vectorCount,
        Runnable warmup
    ) {
    }
}
