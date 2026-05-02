package com.expedit.rinha2026.vectorizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MinutesSinceLastTxCalculatorTest {
    @Test
    void shouldCalculateMinutesUsingUtcWithSeconds() {
        long minutes = MinutesSinceLastTxCalculator.minutesBetween(
            2026, 1, 10, 12, 30, 10,
            2026, 1, 10, 12, 0, 0
        );

        assertEquals(30, minutes);
    }

    @Test
    void shouldClampNegativeDifferenceToZero() {
        long minutes = MinutesSinceLastTxCalculator.minutesBetween(
            2026, 1, 10, 12, 0, 0,
            2026, 1, 10, 12, 30, 0
        );

        assertEquals(0, minutes);
    }
}
