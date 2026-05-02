package com.expedit.rinha2026.bootstrap;

import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.fallback.FallbackScorer;
import com.expedit.rinha2026.http.HttpRequestContext;
import com.expedit.rinha2026.http.HttpServerBootstrap;
import com.expedit.rinha2026.infra.config.MccRiskTableLoader;
import com.expedit.rinha2026.infra.config.NormalizationConfigLoader;
import com.expedit.rinha2026.infra.index.BinaryIndexLoader;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import com.expedit.rinha2026.infra.io.ResourceResolver;
import com.expedit.rinha2026.parser.PayloadParser;
import com.expedit.rinha2026.response.ResponseBuffers;
import com.expedit.rinha2026.response.ResponseWriter;
import com.expedit.rinha2026.search.ExactKnnSearchEngine;
import com.expedit.rinha2026.search.SearchEngine;
import com.expedit.rinha2026.vectorizer.FraudVectorizer;
import java.nio.file.Path;

public final class StartupLoader {
    public void start(AppConfig appConfig, ReadinessState readinessState) throws Exception {
        ResourceResolver resourceResolver = new ResourceResolver();

        NormalizationConfig normalizationConfig = new NormalizationConfigLoader()
            .load(resourceResolver.readUtf8(appConfig.normalizationLocation()));

        MccRiskTable mccRiskTable = new MccRiskTableLoader()
            .load(resourceResolver.readUtf8(appConfig.mccRiskLocation()));

        LoadedIndex loadedIndex = loadIndex(appConfig);
        SearchEngine searchEngine = new ExactKnnSearchEngine(
            loadedIndex.vectors(),
            loadedIndex.labels(),
            loadedIndex.bucketStarts()
        );

        HttpRequestContext context = new HttpRequestContext(
            new PayloadParser(),
            new FraudVectorizer(normalizationConfig, mccRiskTable),
            searchEngine,
            new ResponseBuffers(),
            new ResponseWriter(),
            new FallbackScorer()
        );

        new HttpServerBootstrap().start(appConfig, readinessState, context);
        readinessState.setReady(true);

        System.out.printf("Startup ready on port %d with %d reference vectors.%n",
            appConfig.port(),
            loadedIndex.vectorCount());
    }

    private LoadedIndex loadIndex(AppConfig appConfig) throws Exception {
        if (appConfig.binaryIndexLocation() != null && !appConfig.binaryIndexLocation().isBlank()) {
            return new BinaryIndexLoader().load(Path.of(appConfig.binaryIndexLocation()));
        }

        throw new IllegalStateException("Binary index is required for engine-v2");
    }
}
