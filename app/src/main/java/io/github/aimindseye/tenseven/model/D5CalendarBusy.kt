package io.github.aimindseye.tenseven.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Independent, JVM-testable busy-day policy. Calendar stays read-only until Android takes over event editing. */
data class D5TimeSpan(
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

object D5CalendarBusy {
    private const val MINUTES_PER_DAY = 12 * 60

    fun monthCells(month: YearMonth): List<LocalDate?> {
        val before = month.atDay(1).dayOfWeek.value - 1 // Monday first
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        val cells = List<LocalDate?>(before) { null } + days
        return cells + List((7 - cells.size % 7) % 7) { null }
    }

    /** Merged booked minutes 08:00–20:00, including overlaps only once. */
    fun bookedMinutes(
        date: LocalDate,
        spans: List<D5TimeSpan>,
        zone: ZoneId,
    ): Int {
        val from =
            date
                .atTime(8, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val until =
            date
                .atTime(20, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val intervals =
            spans
                .asSequence()
                .filterNot { it.allDay }
                .map { maxOf(from, it.startMillis) to minOf(until, it.endMillis) }
                .filter { it.second > it.first }
                .sortedBy { it.first }
                .toList()
        var stop = from
        var millis = 0L
        for ((start, end) in intervals) {
            if (end <= stop) continue
            millis += end - maxOf(start, stop)
            stop = end
        }
        return (millis / 60_000L).toInt().coerceIn(0, MINUTES_PER_DAY)
    }

    fun sweep(
        date: LocalDate,
        spans: List<D5TimeSpan>,
        zone: ZoneId,
    ): Float {
        val minutes = bookedMinutes(date, spans, zone)
        return if (minutes == 0) 0f else maxOf(24f, 360f * minutes / MINUTES_PER_DAY)
    }
}
