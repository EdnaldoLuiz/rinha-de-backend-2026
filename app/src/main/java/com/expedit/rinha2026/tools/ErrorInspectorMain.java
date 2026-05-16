package com.expedit.rinha2026.tools;

import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.infra.config.MccRiskTableLoader;
import com.expedit.rinha2026.infra.config.NormalizationConfigLoader;
import com.expedit.rinha2026.infra.index.IvfIndex;
import com.expedit.rinha2026.infra.index.IvfIndexLoader;
import com.expedit.rinha2026.infra.io.ResourceResolver;
import com.expedit.rinha2026.parser.BytePayloadParser;
import com.expedit.rinha2026.search.DistanceKernel;
import com.expedit.rinha2026.search.ExactKnnSearchEngine;
import com.expedit.rinha2026.search.RuntimeBucketKeyEncoder;
import com.expedit.rinha2026.vectorizer.FraudVectorizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ErrorInspectorMain {
    private static final String DEFAULT_NORMALIZATION = "classpath:normalization.json";
    private static final String DEFAULT_MCC_RISK = "classpath:mcc_risk.json";

    public static void main(String[] args) throws Exception {
        String testDataPath = resolveTestDataPath(args);
        String indexPath = resolveIndexPath();

        ResourceResolver resolver = new ResourceResolver();
        NormalizationConfig normalization = new NormalizationConfigLoader()
            .load(resolver.readUtf8(System.getenv().getOrDefault("NORMALIZATION_FILE", DEFAULT_NORMALIZATION)));
        MccRiskTable mccRisk = new MccRiskTableLoader()
            .load(resolver.readUtf8(System.getenv().getOrDefault("MCC_RISK_FILE", DEFAULT_MCC_RISK)));

        IvfIndex index = new IvfIndexLoader().load(Path.of(indexPath));
        ExactKnnSearchEngine exact = new ExactKnnSearchEngine(index.vectors(), index.labels());
        FraudVectorizer vectorizer = new FraudVectorizer(normalization, mccRisk);
        BytePayloadParser parser = new BytePayloadParser();

        int fastNprobe = intSetting("INSPECT_FAST_NPROBE", index.fastNprobe());
        int fullNprobe = intSetting("INSPECT_FULL_NPROBE", index.fullNprobe());
        boolean bboxRepair = "1".equals(System.getenv().getOrDefault("INSPECT_BBOX_REPAIR", "0"));
        Set<String> targetTxIds = parseTargetTxIds();

        byte[] raw = Files.readAllBytes(Path.of(testDataPath));
        List<EntrySlice> entries = parseEntries(raw);
        int errors = 0;

        for (int i = 0; i < entries.size(); i++) {
            EntrySlice entry = entries.get(i);
            if (entry.expectedApproved == null) {
                continue;
            }
            if (!targetTxIds.isEmpty() && (entry.txId == null || !targetTxIds.contains(entry.txId))) {
                continue;
            }

            byte[] payload = Arrays.copyOfRange(raw, entry.request.start, entry.request.end);
            FraudRequestFields req = parser.parse(payload, payload.length);
            QueryVector query = new QueryVector();
            vectorizer.vectorize(req, query);

            IvfDebugResult ivf = inspectIvf(index, query, fastNprobe, fullNprobe, bboxRepair);
            DebugTop5 exactTop5 = inspectExactTop5(index, query.values);
            int exactFraud = exactTop5.fraudCount();
            boolean predictedApproved = ivf.finalFraudCount < 3;

            boolean mismatch = predictedApproved != entry.expectedApproved;
            if (mismatch) {
                errors++;
            }
            if (!mismatch && targetTxIds.isEmpty()) {
                continue;
            }

            System.out.printf(
                "%s idx=%d txId=%s expectedApproved=%s offlineApproved=%s finalFraud=%d fastFraud=%d fullFraud=%d bboxFraud=%d exactFraud=%d%n",
                mismatch ? ("error#" + errors) : "target",
                i,
                entry.txId,
                entry.expectedApproved,
                predictedApproved,
                ivf.finalFraudCount,
                ivf.fastFraudCount,
                ivf.fullFraudCount,
                ivf.bboxFraudCount,
                exactFraud
            );
            System.out.printf(
                "  request amount=%.2f installments=%d mcc=%d merchant=%d hour=%d day=%d tx24h=%d bins=(online=%s cardPresent=%s knownMerchant=%s)%n",
                req.amount,
                req.installments,
                req.merchantMcc,
                req.merchantId,
                req.requestedHour,
                RuntimeBucketKeyEncoder.dayBucket(query.values[4]),
                req.customerTxCount24h,
                req.terminalIsOnline,
                req.terminalCardPresent,
                isKnownMerchant(req)
            );
            System.out.printf(
                "  buckets binary=%d hour=%d day=%d tx=%d%n",
                RuntimeBucketKeyEncoder.binaryBucket(query.values[9], query.values[10], query.values[11]),
                RuntimeBucketKeyEncoder.hourBucket(query.values[3]),
                RuntimeBucketKeyEncoder.dayBucket(query.values[4]),
                RuntimeBucketKeyEncoder.txBucket(query.values[8])
            );
            System.out.printf(
                "  lastTx has=%s ymd=%04d-%02d-%02d hms=%02d:%02d:%02d km=%.4f%n",
                req.hasLastTransaction,
                req.lastYear,
                req.lastMonth,
                req.lastDay,
                req.lastHour,
                req.lastMinute,
                req.lastSecond,
                req.lastKmFromCurrent
            );
            System.out.printf("  top5 distances=%s%n", Arrays.toString(ivf.topDistances));
            System.out.printf("  top5 labels=%s%n", Arrays.toString(ivf.topLabels));
            System.out.printf("  top5 ids=%s%n", Arrays.toString(ivf.topIds));
            System.out.printf("  exact top5 distances=%s%n", Arrays.toString(exactTop5.distances()));
            System.out.printf("  exact top5 labels=%s%n", Arrays.toString(exactTop5.labels()));
            System.out.printf("  exact top5 ids=%s%n", Arrays.toString(exactTop5.ids()));
            System.out.printf("  queryVector=%s%n", Arrays.toString(query.values));
        }

        System.out.printf("done entries=%d errors=%d fast=%d full=%d bboxRepair=%s%n",
            entries.size(), errors, fastNprobe, fullNprobe, bboxRepair);
    }

    private static IvfDebugResult inspectIvf(IvfIndex index, QueryVector query, int fastNprobe, int fullNprobe, boolean bboxRepair) {
        int clusters = index.clusters();
        int probeCount = Math.min(Math.max(1, fullNprobe), clusters);
        int fastLimit = Math.min(Math.max(1, fastNprobe), probeCount);
        int[] selected = new int[probeCount];
        float[] selectedDistances = new float[probeCount];
        float[] clusterDistances = new float[clusters];
        boolean[] visitedClusters = new boolean[clusters];
        DebugTop5 top5 = new DebugTop5();

        Arrays.fill(selected, -1);
        Arrays.fill(selectedDistances, Float.POSITIVE_INFINITY);

        short[] q = query.values;
        float[] centroids = index.centroids();
        for (int dim = 0; dim < q.length; dim++) {
            float qf = q[dim] / 10_000f;
            int base = dim * clusters;
            for (int c = 0; c < clusters; c++) {
                float delta = centroids[base + c] - qf;
                clusterDistances[c] += delta * delta;
            }
        }

        for (int c = 0; c < clusters; c++) {
            float dist = clusterDistances[c];
            if (dist >= selectedDistances[probeCount - 1]) {
                continue;
            }
            int pos = probeCount - 1;
            while (pos > 0 && dist < selectedDistances[pos - 1]) {
                selectedDistances[pos] = selectedDistances[pos - 1];
                selected[pos] = selected[pos - 1];
                pos--;
            }
            selectedDistances[pos] = dist;
            selected[pos] = c;
        }

        scanClusterRange(index, q, top5, selected, 0, fastLimit, visitedClusters);
        int fastFraud = top5.fraudCount();

        scanClusterRange(index, q, top5, selected, fastLimit, probeCount, visitedClusters);
        int fullFraud = top5.fraudCount();
        int bboxFraud = fullFraud;

        if (bboxRepair && (fullFraud == 2 || fullFraud == 3)) {
            for (int c = 0; c < clusters; c++) {
                if (visitedClusters[c]) {
                    continue;
                }
                if (bboxLowerBound(index, q, c) <= top5.currentWorst()) {
                    visitedClusters[c] = true;
                    scanCluster(index, q, top5, c);
                }
            }
            bboxFraud = top5.fraudCount();
        }

        return new IvfDebugResult(
            fastFraud,
            fullFraud,
            bboxFraud,
            top5.fraudCount(),
            top5.distances(),
            top5.labels(),
            top5.ids()
        );
    }

    private static void scanClusterRange(
        IvfIndex index,
        short[] query,
        DebugTop5 top5,
        int[] selected,
        int start,
        int end,
        boolean[] visitedClusters
    ) {
        for (int i = start; i < end; i++) {
            int cluster = selected[i];
            if (cluster < 0) {
                continue;
            }
            visitedClusters[cluster] = true;
            scanCluster(index, query, top5, cluster);
        }
    }

    private static void scanCluster(IvfIndex index, short[] query, DebugTop5 top5, int cluster) {
        int start = index.offsets()[cluster];
        int end = index.offsets()[cluster + 1];
        short[] vectors = index.vectors();
        byte[] labels = index.labels();
        int[] ids = index.origIds();

        for (int row = start; row < end; row++) {
            int offset = row * query.length;
            long dist = DistanceKernel.squaredDistanceEarlyAbort(query, vectors, offset, top5.currentWorst());
            if (dist < top5.currentWorst()) {
                top5.offer(dist, labels[row], ids[row]);
            }
        }
    }

    private static long bboxLowerBound(IvfIndex index, short[] query, int cluster) {
        int base = cluster * query.length;
        short[] min = index.bboxMin();
        short[] max = index.bboxMax();
        long sum = 0L;

        for (int d = 0; d < query.length; d++) {
            int v = query[d];
            int low = min[base + d];
            int high = max[base + d];
            int delta = 0;
            if (v < low) {
                delta = low - v;
            } else if (v > high) {
                delta = v - high;
            }
            sum += (long) delta * delta;
        }

        return sum;
    }

    private static boolean isKnownMerchant(FraudRequestFields req) {
        for (int i = 0; i < req.knownMerchantCount; i++) {
            if (req.knownMerchantIds[i] == req.merchantId) {
                return true;
            }
        }
        return false;
    }

    private static DebugTop5 inspectExactTop5(IvfIndex index, short[] query) {
        DebugTop5 top5 = new DebugTop5();
        short[] vectors = index.vectors();
        byte[] labels = index.labels();
        int[] ids = index.origIds();

        for (int row = 0; row < labels.length; row++) {
            int offset = row * query.length;
            long dist = DistanceKernel.squaredDistanceEarlyAbort(query, vectors, offset, top5.currentWorst());
            if (dist < top5.currentWorst()) {
                top5.offer(dist, labels[row], ids[row]);
            }
        }

        return top5;
    }

    private static String resolveTestDataPath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        String env = System.getenv("TEST_DATA_FILE");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String repo = System.getenv("RINHA_2026_REPO");
        if (repo != null && !repo.isBlank()) {
            return Path.of(repo, "test", "test-data.json").toString();
        }
        throw new IllegalArgumentException("missing test data path. use arg[0] or TEST_DATA_FILE or RINHA_2026_REPO");
    }

    private static String resolveIndexPath() {
        String env = System.getenv("INDEX_BINARY_FILE");
        if (env == null || env.isBlank()) {
            return "app/src/main/resources/fraud-index.dat";
        }
        return env;
    }

    private static int intSetting(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(raw);
    }

    private static List<EntrySlice> parseEntries(byte[] json) {
        List<EntrySlice> entries = new ArrayList<>();
        int entriesKey = indexOfAscii(json, "\"entries\"", 0);
        if (entriesKey < 0) {
            return entries;
        }
        int arrayStart = findArrayStartAfterKey(json, entriesKey);
        if (arrayStart < 0) {
            return entries;
        }
        Range entriesArray = findArrayRange(json, arrayStart);
        if (entriesArray == null) {
            return entries;
        }

        List<Range> objects = splitObjects(json, entriesArray.start + 1, entriesArray.end - 1);
        for (Range object : objects) {
            int requestKey = indexOfAscii(json, "\"request\"", object.start);
            if (requestKey < 0 || requestKey >= object.end) {
                continue;
            }
            Range requestObj = findObjectForKey(json, requestKey);
            if (requestObj == null || requestObj.start < object.start || requestObj.end > object.end) {
                continue;
            }

            Boolean expectedApproved = findExpectedApprovedInEntry(json, object.start, object.end);
            String txId = findTxId(json, requestObj.start, requestObj.end);
            entries.add(new EntrySlice(requestObj, expectedApproved, txId));
        }

        return entries;
    }

    private static List<Range> splitObjects(byte[] json, int start, int end) {
        List<Range> ranges = new ArrayList<>();
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = Math.max(0, start); i < Math.min(json.length, end); i++) {
            byte c = json[i];
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    ranges.add(new Range(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return ranges;
    }

    private static Boolean findExpectedApprovedInEntry(byte[] json, int entryStart, int entryEnd) {
        int infoKey = indexOfAscii(json, "\"info\"", entryStart);
        if (infoKey < 0 || infoKey >= entryEnd) {
            return null;
        }
        Range infoObj = findObjectForKey(json, infoKey);
        if (infoObj == null || infoObj.start < entryStart || infoObj.end > entryEnd) {
            return null;
        }

        int expectedKey = indexOfAscii(json, "\"expected_response\"", infoObj.start);
        if (expectedKey < 0 || expectedKey >= infoObj.end) {
            return null;
        }
        Range expectedObj = findObjectForKey(json, expectedKey);
        if (expectedObj == null || expectedObj.start < infoObj.start || expectedObj.end > infoObj.end) {
            return null;
        }
        return findApprovedBoolean(json, expectedObj.start, expectedObj.end);
    }

    private static Boolean findApprovedBoolean(byte[] json, int start, int end) {
        int idx = indexOfAscii(json, "\"approved\"", start);
        if (idx < 0 || idx >= end) {
            return null;
        }
        int colon = -1;
        for (int i = idx; i < end; i++) {
            if (json[i] == ':') {
                colon = i + 1;
                break;
            }
        }
        if (colon < 0) {
            return null;
        }
        while (colon < end && isWs(json[colon])) {
            colon++;
        }
        if (startsWith(json, colon, end, "true")) {
            return true;
        }
        if (startsWith(json, colon, end, "false")) {
            return false;
        }
        return null;
    }

    private static Range findObjectForKey(byte[] json, int keyIdx) {
        int colon = -1;
        for (int i = keyIdx; i < json.length; i++) {
            if (json[i] == ':') {
                colon = i + 1;
                break;
            }
        }
        if (colon < 0) {
            return null;
        }
        while (colon < json.length && isWs(json[colon])) {
            colon++;
        }
        if (colon >= json.length || json[colon] != '{') {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = colon; i < json.length; i++) {
            byte c = json[i];
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return new Range(colon, i + 1);
                }
            }
        }
        return null;
    }

    private static int findArrayStartAfterKey(byte[] json, int keyIdx) {
        int colon = -1;
        for (int i = keyIdx; i < json.length; i++) {
            if (json[i] == ':') {
                colon = i + 1;
                break;
            }
        }
        if (colon < 0) {
            return -1;
        }
        while (colon < json.length && isWs(json[colon])) {
            colon++;
        }
        if (colon >= json.length || json[colon] != '[') {
            return -1;
        }
        return colon;
    }

    private static Range findArrayRange(byte[] json, int startBracket) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = startBracket; i < json.length; i++) {
            byte c = json[i];
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return new Range(startBracket, i + 1);
                }
            }
        }
        return null;
    }

    private static int indexOfAscii(byte[] haystack, String needle, int from) {
        byte[] n = needle.getBytes();
        int max = haystack.length - n.length;
        outer:
        for (int i = Math.max(0, from); i <= max; i++) {
            if (haystack[i] != n[0]) {
                continue;
            }
            for (int j = 1; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static boolean startsWith(byte[] json, int start, int end, String token) {
        if (start < 0 || start + token.length() > end) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (json[start + i] != (byte) token.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWs(byte c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    private record Range(int start, int end) {
    }

    private static String findTxId(byte[] json, int start, int end) {
        int idIdx = indexOfAsciiInRange(json, "\"id\"", start, end);
        if (idIdx < 0) {
            return null;
        }
        int tokenStart = findQuotedValueStart(json, end, idIdx);
        if (tokenStart < 0) {
            return null;
        }
        int tokenEnd = findClosingQuote(json, end, tokenStart);
        if (tokenEnd < 0 || tokenEnd <= tokenStart) {
            return null;
        }
        return new String(json, tokenStart, tokenEnd - tokenStart, StandardCharsets.US_ASCII);
    }

    private static int indexOfAsciiInRange(byte[] haystack, String needle, int fromInclusive, int toExclusive) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        int max = Math.min(haystack.length, toExclusive) - n.length;
        outer:
        for (int i = Math.max(0, fromInclusive); i <= max; i++) {
            if (haystack[i] != n[0]) {
                continue;
            }
            for (int j = 1; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int findQuotedValueStart(byte[] json, int length, int keyIdx) {
        int valueStart = findValueStart(json, length, keyIdx);
        if (valueStart < 0 || valueStart >= length || json[valueStart] != '"') {
            return -1;
        }
        return valueStart + 1;
    }

    private static int findValueStart(byte[] json, int length, int keyIdx) {
        int colon = -1;
        for (int i = keyIdx; i < length; i++) {
            if (json[i] == ':') {
                colon = i;
                break;
            }
        }
        if (colon < 0) {
            return -1;
        }
        int i = colon + 1;
        while (i < length) {
            byte c = json[i];
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int findClosingQuote(byte[] json, int length, int start) {
        for (int i = start; i < length; i++) {
            if (json[i] == '"') {
                return i;
            }
        }
        return -1;
    }

    private static Set<String> parseTargetTxIds() {
        String raw = System.getenv().getOrDefault("INSPECT_TX_IDS", "").trim();
        if (raw.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private record EntrySlice(Range request, Boolean expectedApproved, String txId) {
    }

    private record IvfDebugResult(
        int fastFraudCount,
        int fullFraudCount,
        int bboxFraudCount,
        int finalFraudCount,
        long[] topDistances,
        byte[] topLabels,
        int[] topIds
    ) {
    }

    private static final class DebugTop5 {
        private final long[] distances = new long[] {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
        private final byte[] labels = new byte[5];
        private final int[] ids = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};

        long currentWorst() {
            return distances[4];
        }

        int fraudCount() {
            return labels[0] + labels[1] + labels[2] + labels[3] + labels[4];
        }

        void offer(long dist, byte label, int id) {
            for (int pos = 0; pos < 5; pos++) {
                if (!better(dist, id, distances[pos], ids[pos])) {
                    continue;
                }
                for (int s = 4; s > pos; s--) {
                    distances[s] = distances[s - 1];
                    labels[s] = labels[s - 1];
                    ids[s] = ids[s - 1];
                }
                distances[pos] = dist;
                labels[pos] = label;
                ids[pos] = id;
                return;
            }
        }

        long[] distances() {
            return Arrays.copyOf(distances, distances.length);
        }

        byte[] labels() {
            return Arrays.copyOf(labels, labels.length);
        }

        int[] ids() {
            return Arrays.copyOf(ids, ids.length);
        }

        private boolean better(long distA, int idA, long distB, int idB) {
            return distA < distB || (distA == distB && idA < idB);
        }
    }
}
