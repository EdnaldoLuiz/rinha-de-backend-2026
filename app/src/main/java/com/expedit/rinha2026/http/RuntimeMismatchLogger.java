package com.expedit.rinha2026.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class RuntimeMismatchLogger {
    private static final String ENTRIES_KEY = "\"entries\"";
    private static final String REQUEST_KEY = "\"request\"";
    private static final String EXPECTED_RESPONSE_KEY = "\"expected_response\"";
    private static final String APPROVED_KEY = "\"approved\"";
    private static final String ID_KEY = "\"id\"";

    private final Map<String, Boolean> expectedByTxId;
    private final Set<String> loggedTxIds;
    private final int maxLogs;
    private int logCount;

    private RuntimeMismatchLogger(Map<String, Boolean> expectedByTxId, int maxLogs) {
        this.expectedByTxId = expectedByTxId;
        this.loggedTxIds = new HashSet<>();
        this.maxLogs = Math.max(1, maxLogs);
        this.logCount = 0;
    }

    static RuntimeMismatchLogger loadFromEnv() {
        String path = resolvePath();
        if (path == null || path.isBlank()) {
            System.out.println("RuntimeMismatchLogger: disabled (missing RUNTIME_MISMATCH_FILE or RINHA_2026_REPO)");
            return null;
        }

        try {
            byte[] json = Files.readAllBytes(Path.of(path));
            Map<String, Boolean> expected = parseExpectedByTxId(json);
            if (expected.isEmpty()) {
                System.out.printf("RuntimeMismatchLogger: disabled (no entries loaded from %s)%n", path);
                return null;
            }
            int maxLogs = intEnv("RUNTIME_MISMATCH_MAX_LOGS", 20);
            System.out.printf("RuntimeMismatchLogger: loaded %d entries from %s%n", expected.size(), path);
            return new RuntimeMismatchLogger(expected, maxLogs);
        } catch (IOException e) {
            System.out.printf("RuntimeMismatchLogger: disabled (failed to read %s: %s)%n", path, e.getMessage());
            return null;
        }
    }

    void checkAndLog(String txId, boolean actualApproved, int fraudCount) {
        if (txId == null || txId.isBlank()) {
            return;
        }
        Boolean expectedApproved = expectedByTxId.get(txId);
        if (expectedApproved == null || expectedApproved == actualApproved) {
            return;
        }
        if (logCount >= maxLogs || loggedTxIds.contains(txId)) {
            return;
        }
        loggedTxIds.add(txId);
        logCount++;
        System.out.printf(
            "RuntimeMismatch: txId=%s expectedApproved=%s actualApproved=%s fraudCount=%d%n",
            txId,
            expectedApproved,
            actualApproved,
            fraudCount
        );
    }

    static String extractTransactionId(byte[] body, int length) {
        int idx = 0;
        while (idx < length) {
            int idKey = indexOf(body, length, ID_KEY, idx);
            if (idKey < 0) {
                return null;
            }
            int valueStart = findQuotedValueStart(body, length, idKey);
            if (valueStart < 0) {
                idx = idKey + ID_KEY.length();
                continue;
            }
            int valueEnd = findClosingQuote(body, length, valueStart);
            if (valueEnd < 0) {
                return null;
            }
            int valueLength = valueEnd - valueStart;
            if (valueLength >= 3
                && body[valueStart] == 't'
                && body[valueStart + 1] == 'x'
                && body[valueStart + 2] == '-') {
                return new String(body, valueStart, valueLength, StandardCharsets.US_ASCII);
            }
            idx = valueEnd + 1;
        }
        return null;
    }

    private static Map<String, Boolean> parseExpectedByTxId(byte[] json) {
        Map<String, Boolean> map = new HashMap<>();
        int entriesIdx = indexOf(json, json.length, ENTRIES_KEY, 0);
        if (entriesIdx < 0) {
            return map;
        }
        int arrayStart = findArrayStartAfterKey(json, json.length, entriesIdx);
        if (arrayStart < 0) {
            return map;
        }
        int idx = arrayStart + 1;
        while (idx < json.length) {
            idx = skipWs(json, json.length, idx);
            if (idx >= json.length || json[idx] == ']') {
                break;
            }
            if (json[idx] == ',') {
                idx++;
                continue;
            }
            if (json[idx] != '{') {
                idx++;
                continue;
            }
            int entryEnd = findMatching(json, json.length, idx, '{', '}');
            if (entryEnd < 0) {
                break;
            }
            String txId = readTxIdInEntry(json, idx, entryEnd + 1);
            Boolean approved = readExpectedApprovedInEntry(json, idx, entryEnd + 1);
            if (txId != null && approved != null) {
                map.put(txId, approved);
            }
            idx = entryEnd + 1;
        }
        return map;
    }

    private static String readTxIdInEntry(byte[] json, int start, int endExclusive) {
        int requestKey = indexOf(json, endExclusive, REQUEST_KEY, start);
        if (requestKey < 0) {
            return null;
        }
        int requestStart = findObjectStartAfterKey(json, endExclusive, requestKey);
        if (requestStart < 0) {
            return null;
        }
        int requestEnd = findMatching(json, endExclusive, requestStart, '{', '}');
        if (requestEnd < 0) {
            return null;
        }
        int idKey = indexOf(json, requestEnd + 1, ID_KEY, requestStart);
        if (idKey < 0) {
            return null;
        }
        int valueStart = findQuotedValueStart(json, requestEnd + 1, idKey);
        if (valueStart < 0) {
            return null;
        }
        int valueEnd = findClosingQuote(json, requestEnd + 1, valueStart);
        if (valueEnd < 0) {
            return null;
        }
        int len = valueEnd - valueStart;
        if (len <= 0) {
            return null;
        }
        return new String(json, valueStart, len, StandardCharsets.US_ASCII);
    }

    private static Boolean readExpectedApprovedInEntry(byte[] json, int start, int endExclusive) {
        int expectedKey = indexOf(json, endExclusive, EXPECTED_RESPONSE_KEY, start);
        if (expectedKey < 0) {
            return null;
        }
        int expectedStart = findObjectStartAfterKey(json, endExclusive, expectedKey);
        if (expectedStart < 0) {
            return null;
        }
        int expectedEnd = findMatching(json, endExclusive, expectedStart, '{', '}');
        if (expectedEnd < 0) {
            return null;
        }
        int approvedKey = indexOf(json, expectedEnd + 1, APPROVED_KEY, expectedStart);
        if (approvedKey < 0) {
            return null;
        }
        int valueStart = findValueStart(json, expectedEnd + 1, approvedKey);
        if (valueStart < 0) {
            return null;
        }
        if (startsWith(json, expectedEnd + 1, valueStart, "true")) {
            return true;
        }
        if (startsWith(json, expectedEnd + 1, valueStart, "false")) {
            return false;
        }
        return null;
    }

    private static String resolvePath() {
        String direct = System.getenv("RUNTIME_MISMATCH_FILE");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String repo = System.getenv("RINHA_2026_REPO");
        if (repo != null && !repo.isBlank()) {
            return Path.of(repo, "test", "test-data.json").toString();
        }
        return null;
    }

    private static int intEnv(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(raw);
    }

    private static int indexOf(byte[] json, int length, String token, int from) {
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        int max = length - needle.length;
        outer:
        for (int i = Math.max(0, from); i <= max; i++) {
            if (json[i] != needle[0]) {
                continue;
            }
            for (int j = 1; j < needle.length; j++) {
                if (json[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int findArrayStartAfterKey(byte[] json, int length, int keyIdx) {
        int colon = findColon(json, length, keyIdx);
        if (colon < 0) {
            return -1;
        }
        int start = skipWs(json, length, colon + 1);
        return (start < length && json[start] == '[') ? start : -1;
    }

    private static int findObjectStartAfterKey(byte[] json, int length, int keyIdx) {
        int colon = findColon(json, length, keyIdx);
        if (colon < 0) {
            return -1;
        }
        int start = skipWs(json, length, colon + 1);
        return (start < length && json[start] == '{') ? start : -1;
    }

    private static int findQuotedValueStart(byte[] json, int length, int keyIdx) {
        int valueStart = findValueStart(json, length, keyIdx);
        if (valueStart < 0 || valueStart >= length || json[valueStart] != '"') {
            return -1;
        }
        return valueStart + 1;
    }

    private static int findValueStart(byte[] json, int length, int keyIdx) {
        int colon = findColon(json, length, keyIdx);
        if (colon < 0) {
            return -1;
        }
        return skipWs(json, length, colon + 1);
    }

    private static int findColon(byte[] json, int length, int from) {
        for (int i = Math.max(0, from); i < length; i++) {
            if (json[i] == ':') {
                return i;
            }
        }
        return -1;
    }

    private static int findClosingQuote(byte[] json, int length, int from) {
        for (int i = from; i < length; i++) {
            if (json[i] == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findMatching(byte[] json, int endExclusive, int start, char open, char close) {
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        for (int i = start; i < endExclusive; i++) {
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
            if (c == (byte) open) {
                depth++;
            } else if (c == (byte) close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWs(byte[] json, int length, int from) {
        int i = from;
        while (i < length) {
            byte c = json[i];
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return i;
            }
            i++;
        }
        return i;
    }

    private static boolean startsWith(byte[] json, int length, int start, String token) {
        if (start < 0 || start + token.length() > length) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (json[start + i] != (byte) token.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
