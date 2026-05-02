package com.expedit.rinha2026.vectorizer;

public final class MinutesSinceLastTxCalculator {
    private MinutesSinceLastTxCalculator() {
    }

    public static long minutesBetween(
        int year,
        int month,
        int day,
        int hour,
        int minute,
        int second,
        int lastYear,
        int lastMonth,
        int lastDay,
        int lastHour,
        int lastMinute,
        int lastSecond
    ) {
        long currentEpochSeconds = UtcDateTimeMath.epochSecond(year, month, day, hour, minute, second);
        long lastEpochSeconds = UtcDateTimeMath.epochSecond(lastYear, lastMonth, lastDay, lastHour, lastMinute, lastSecond);
        long minutes = (currentEpochSeconds - lastEpochSeconds) / 60L;
        return Math.max(minutes, 0L);
    }
}
