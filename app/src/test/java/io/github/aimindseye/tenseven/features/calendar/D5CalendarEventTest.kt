package io.github.aimindseye.tenseven.features.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class D5CalendarEventTest {
    private fun event(
        start: Long,
        end: Long,
        allDay: Boolean,
    ) = D5CalendarEvent(
        id = 1,
        title = "Test event",
        startMillis = start,
        endMillis = end,
        allDay = allDay,
        calendarId = 4,
        calendarName = "Personal",
    )

    @Test
    fun allDayUsesUtcDateRatherThanLocalZoneAndExclusiveEnd() {
        val first = LocalDate.of(2026, 11, 9)
        val start = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end =
            first
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val item = event(start, end, true)
        val westCoast = ZoneId.of("America/Los_Angeles")
        assertTrue(item.overlaps(first, westCoast))
        assertFalse(item.overlaps(first.minusDays(1), westCoast))
        assertFalse(item.overlaps(first.plusDays(1), westCoast))
    }

    @Test
    fun timedEventsOverlapOnlyDaysTheyActuallyIntersect() {
        val zone = ZoneId.of("America/New_York")
        val first = LocalDate.of(2026, 11, 9)
        val start =
            first
                .atTime(23, 45)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val end =
            first
                .plusDays(1)
                .atTime(0, 15)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val item = event(start, end, false)
        assertTrue(item.overlaps(first, zone))
        assertTrue(item.overlaps(first.plusDays(1), zone))
        assertFalse(item.overlaps(first.minusDays(1), zone))
        assertFalse(item.overlaps(first.plusDays(2), zone))
    }

    @Test
    fun timedEventEndingExactlyAtMidnightDoesNotOverlapNextDay() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 9)
        val start =
            day
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val end =
            day
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val item = event(start, end, false)
        assertTrue(item.overlaps(day, zone))
        assertFalse(item.overlaps(day.plusDays(1), zone))
    }
}
