package com.expedit.rinha2026.tools.inspector;

import com.expedit.rinha2026.infra.index.LoadedIndex;

public final class IndexDumpPrinter {
    public void printSummary(LoadedIndex index) {
        System.out.printf("Index summary: vectors=%d stride=%d dims=%d buckets=%d%n",
            index.labels().length,
            index.stride(),
            index.dimensions(),
            index.buckets().length
        );
    }
}
