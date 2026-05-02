package com.expedit.rinha2026.parser;

public final class FastNumberParser {
    private FastNumberParser() {
    }

    public static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    public static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }
}
