package com.expedit.rinha2026.tools.preprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BucketAccumulator {
    public Map<Integer, List<ReferenceRecord>> groupByBucket(List<ReferenceRecord> records) {
        Map<Integer, List<ReferenceRecord>> grouped = new LinkedHashMap<>();
        for (ReferenceRecord record : records) {
            grouped.computeIfAbsent(record.bucketKey(), ignored -> new ArrayList<>()).add(record);
        }
        return grouped;
    }
}
