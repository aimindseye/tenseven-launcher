package io.github.aimindseye.tenseven.features.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class D6TodayNavigationPolicyTest {
    private val today = LocalDate.of(2026, 11, 9)
    @Test fun previousMonthRestoresTodayAndReloads() {
        val result = D6TodayNavigationPolicy.reset(today, YearMonth.of(2026, 10))
        assertEquals(today, result.day)
        assertEquals(YearMonth.of(2026, 11), result.month)
        assertTrue(result.reloadEvents)
    }
    @Test fun futureMonthRestoresTodayAndReloads() {
        assertTrue(D6TodayNavigationPolicy.reset(today, YearMonth.of(2027, 1)).reloadEvents)
    }
    @Test fun sameMonthDayChangeRestoresTodayWithoutRedundantReload() {
        val result = D6TodayNavigationPolicy.reset(today, YearMonth.of(2026, 11))
        assertEquals(today, result.day)
        assertFalse(result.reloadEvents)
    }
    @Test fun decemberJanuaryRolloverIsHandled() {
        val now = LocalDate.of(2027, 1, 1)
        assertEquals(YearMonth.of(2027, 1), D6TodayNavigationPolicy.reset(now, YearMonth.of(2026, 12)).month)
    }
}
