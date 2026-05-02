package com.expedit.rinha2026.tools.inspector;

import com.expedit.rinha2026.infra.index.BinaryIndexLoader;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import java.nio.file.Path;

public final class IndexInspectorMain {
    public static void main(String[] args) throws Exception {
        String input = args.length > 0 ? args[0] : "app/src/main/resources/fraud-index.dat";
        LoadedIndex index = new BinaryIndexLoader().load(Path.of(input));
        new IndexDumpPrinter().printSummary(index);
    }
}
