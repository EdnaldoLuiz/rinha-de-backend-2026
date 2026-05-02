package com.expedit.rinha2026.vectorizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DayOfWeekCalculatorTest {
    @Test
    void shouldMapMondayToZeroAndSundayToSix() {
        assertEquals(0, DayOfWeekCalculator.dayOfWeek(2026, 1, 12)); // Monday
        assertEquals(6, DayOfWeekCalculator.dayOfWeek(2026, 1, 11)); // Sunday
    }
}
