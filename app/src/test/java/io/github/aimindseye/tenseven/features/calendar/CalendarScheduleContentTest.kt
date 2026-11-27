package io.github.aimindseye.tenseven.features.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarScheduleContentTest {
    private val day = LocalDate.parse("2026-11-01")
    private val zone = ZoneId.of("America/New_York")
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val end =
        day
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private fun event(
        id: Long,
        from: Long,
        to: Long,
    ) = D5R2Event(id, id, "Event", from, to, false)

    @Test
    fun excludesEventsAtEitherExclusiveBoundary() {
        val before = event(1L, start - 1L, start)
        val after = event(2L, end, end + 1L)
        assertEquals(emptyList<D5R2Event>(), calendarEventsOn(listOf(before, after), day, zone))
    }

    @Test
    fun includesOvernightAndDaylightSavingInstancesInStableStartOrder() {
        val first = event(1L, start - 1L, start + 1L)
        val second = event(2L, start, end)
        val sameStart = event(3L, start, end)
        val last = event(4L, end - 1L, end + 1L)
        assertEquals(
            listOf(first, second, sameStart, last),
            calendarEventsOn(listOf(last, first, second, sameStart), day, zone),
        )
    }

    @Test
    fun respectsSuppliedVisibilityAndRetainsAllDayInstances() {
        val visible = D5R2Event(1L, 1L, "All day", start, end, true)
        val hidden = event(2L, start, end)
        assertEquals(
            listOf(visible),
            calendarEventsOn(listOf(visible, hidden).filter { it.calendarId == 1L }, day, zone),
        )
        assertEquals(emptyList<D5R2Event>(), calendarEventsOn(emptyList(), day, zone))
    }
}
