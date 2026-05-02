package com.expedit.rinha2026.vectorizer;

final class UtcDateTimeMath {
    private static final long DAYS_0000_TO_1970 = 719528L;

    private UtcDateTimeMath() {
    }

    static int dayOfWeekMondayZero(int year, int month, int day) {
        long epochDay = epochDay(year, month, day);
        return (int) Math.floorMod(epochDay + 3L, 7L);
    }

    static long epochSecond(int year, int month, int day, int hour, int minute, int second) {
        return (epochDay(year, month, day) * 86400L)
            + (hour * 3600L)
            + (minute * 60L)
            + second;
    }

    private static long epochDay(int year, int month, int day) {
        long y = year;
        long m = month;
        long total = 365L * y;

        if (y >= 0L) {
            total += (y + 3L) / 4L - (y + 99L) / 100L + (y + 399L) / 400L;
        } else {
            total -= y / -4L - y / -100L + y / -400L;
        }

        total += ((367L * m) - 362L) / 12L;
        total += day - 1L;

        if (m > 2L) {
            total -= isLeapYear(y) ? 1L : 2L;
        }

        return total - DAYS_0000_TO_1970;
    }

    private static boolean isLeapYear(long year) {
        return ((year & 3L) == 0L) && ((year % 100L) != 0L || (year % 400L) == 0L);
    }
}
