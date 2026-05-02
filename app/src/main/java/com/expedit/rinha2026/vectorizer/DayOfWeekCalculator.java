package com.expedit.rinha2026.vectorizer;

public final class DayOfWeekCalculator {
    private DayOfWeekCalculator() {
    }

    public static int dayOfWeek(int year, int month, int day) {
        return UtcDateTimeMath.dayOfWeekMondayZero(year, month, day);
    }
}
