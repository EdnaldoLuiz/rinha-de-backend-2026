package com.expedit.rinha2026.tools.preprocessor;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.infra.index.LoadedIndex;
import com.expedit.rinha2026.infra.index.ReferenceDatasetLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PreprocessorMain {
    private static final String DEFAULT_INPUT = "resources/references.json.gz";
    private static final String DEFAULT_FALLBACK_INPUT = "app/src/main/resources/references.json";
    private static final String DEFAULT_OUTPUT = "app/src/main/resources/fraud-index.dat";

    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : defaultInputPath();
        Path output = args.length > 1 ? Path.of(args[1]) : Path.of(DEFAULT_OUTPUT);

        String json = new ReferenceJsonReader().read(input);
        LoadedIndex parsed = new ReferenceDatasetLoader().load(json);

        List<ReferenceRecord> records = toReferenceRecords(parsed);
        LoadedIndex indexed = new BucketedShortIndexBuilder().build(records);

        new BinaryIndexWriter().write(output, indexed);

        PreprocessorReport report = new PreprocessorReport(
            indexed.labels().length,
            indexed.bucketStarts().length - 1,
            Files.size(output)
        );

        System.out.printf("Preprocessor done. vectors=%d buckets=%d output=%s bytes=%d%n",
            report.vectors(),
            report.buckets(),
            output,
            report.bytes());
    }

    private static List<ReferenceRecord> toReferenceRecords(LoadedIndex index) {
        List<ReferenceRecord> records = new ArrayList<>(index.labels().length);
        BucketKeyEncoder encoder = new BucketKeyEncoder();

        for (int row = 0; row < index.labels().length; row++) {
            int offset = row * index.dimensions();
            float[] vector14 = new float[Constants.VECTOR_DIMENSIONS];
            for (int d = 0; d < Constants.VECTOR_DIMENSIONS; d++) {
                vector14[d] = index.vectors()[offset + d] / 10000.0f;
            }
            int key = encoder.encode(vector14);
            records.add(new ReferenceRecord(vector14, index.labels()[row], key));
        }

        return records;
    }

    private static Path defaultInputPath() {
        Path gz = Path.of(DEFAULT_INPUT);
        if (Files.exists(gz)) {
            return gz;
        }
        return Path.of(DEFAULT_FALLBACK_INPUT);
    }
}
