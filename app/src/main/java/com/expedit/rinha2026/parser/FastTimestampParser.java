package com.expedit.rinha2026.parser;

public final class FastTimestampParser {
    private FastTimestampParser() {
    }

    private static void validate(String ts) {
        // Expected format: YYYY-MM-DDTHH:mm:ssZ
        if (ts == null || ts.length() < 20) {
            throw new IllegalArgumentException("Invalid timestamp format");
        }
    }

    public static int year(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(0, 4));
    }

    public static int month(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(5, 7));
    }

    public static int day(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(8, 10));
    }

    public static int hour(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(11, 13));
    }

    public static int minute(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(14, 16));
    }

    public static int second(String ts) {
        validate(ts);
        return Integer.parseInt(ts.substring(17, 19));
    }
}
