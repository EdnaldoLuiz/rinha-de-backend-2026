package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import com.expedit.rinha2026.infra.index.ReferenceDatasetLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PreprocessorMain {
    private static final String DEFAULT_INPUT = "resources/references.json.gz";
    private static final String DEFAULT_FALLBACK_INPUT = "app/src/main/resources/references.json";
    private static final String DEFAULT_OUTPUT = "app/src/main/resources/fraud-index.dat";
    private static final int DEFAULT_IVF_CLUSTERS = 512;
    private static final int DEFAULT_IVF_FAST_NPROBE = 8;
    private static final int DEFAULT_IVF_FULL_NPROBE = 16;
    private static final int DEFAULT_IVF_SAMPLE_SIZE = 20_000;
    private static final int DEFAULT_IVF_ITERATIONS = 6;
    private static final String DEFAULT_INDEX_MODE = "ivf";

    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : defaultInputPath();
        Path output = args.length > 1 ? Path.of(args[1]) : Path.of(DEFAULT_OUTPUT);

        String json = new ReferenceJsonReader().read(input);
        LoadedIndex parsed = new ReferenceDatasetLoader().load(json);

        String indexMode = setting("INDEX_MODE", DEFAULT_INDEX_MODE).toLowerCase(Locale.ROOT);
        List<ReferenceRecord> records = toReferenceRecords(parsed, "bucket".equals(indexMode));

        PreprocessorReport report;
        if ("bucket".equals(indexMode)) {
            LoadedIndex indexed = new BucketedShortIndexBuilder().build(records);
            new BinaryIndexWriter().write(output, indexed);
            report = new PreprocessorReport(indexed.labels().length, Constants.BUCKET_COUNT, Files.size(output));
        } else {
            var indexed = new IvfIndexBuilder().build(
                records,
                intSetting("IVF_CLUSTERS", DEFAULT_IVF_CLUSTERS),
                intSetting("IVF_FAST_NPROBE", DEFAULT_IVF_FAST_NPROBE),
                intSetting("IVF_FULL_NPROBE", DEFAULT_IVF_FULL_NPROBE),
                intSetting("IVF_SAMPLE_SIZE", DEFAULT_IVF_SAMPLE_SIZE),
                intSetting("IVF_ITERATIONS", DEFAULT_IVF_ITERATIONS)
            );
            new IvfBinaryWriter().write(output, indexed);
            report = new PreprocessorReport(indexed.labels().length, indexed.clusters(), Files.size(output));
        }

        System.out.printf("Preprocessor done. vectors=%d buckets=%d output=%s bytes=%d%n",
            report.vectors(),
            report.buckets(),
            output,
            report.bytes());
    }

    private static List<ReferenceRecord> toReferenceRecords(LoadedIndex index, boolean withBucketKey) {
        List<ReferenceRecord> records = new ArrayList<>(index.labels().length);
        BucketKeyEncoder bucketKeyEncoder = withBucketKey ? new BucketKeyEncoder() : null;

        for (int row = 0; row < index.labels().length; row++) {
            int offset = row * index.dimensions();
            float[] vector14 = new float[Constants.VECTOR_DIMENSIONS];
            for (int d = 0; d < Constants.VECTOR_DIMENSIONS; d++) {
                vector14[d] = index.vectors()[offset + d] / (float) Constants.VECTOR_SCALE;
            }
            int bucketKey = withBucketKey ? bucketKeyEncoder.encode(vector14) : 0;
            records.add(new ReferenceRecord(vector14, index.labels()[row], bucketKey));
        }

        return records;
    }

    private static int intSetting(String name, int fallback) {
        String raw = setting(name, null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(raw);
    }

    private static String setting(String name, String fallback) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(name);
        }
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw;
    }

    private static Path defaultInputPath() {
        Path gz = Path.of(DEFAULT_INPUT);
        if (Files.exists(gz)) {
            return gz;
        }
        return Path.of(DEFAULT_FALLBACK_INPUT);
    }
}
