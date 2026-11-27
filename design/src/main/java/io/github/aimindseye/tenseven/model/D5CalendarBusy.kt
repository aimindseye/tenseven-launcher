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
    private const val DAYS_PER_WEEK = 7
    private const val WINDOW_START_HOUR = 8
    private const val WINDOW_END_HOUR = 20
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MIN_SWEEP_DEGREES = 24f
    private const val FULL_SWEEP_DEGREES = 360f
    private const val MINUTES_PER_DAY = (WINDOW_END_HOUR - WINDOW_START_HOUR) * MINUTES_PER_HOUR

    fun monthCells(month: YearMonth): List<LocalDate?> {
        val before = month.atDay(1).dayOfWeek.value - 1 // Monday first
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        val cells = List<LocalDate?>(before) { null } + days
        return cells + List((DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK) { null }
    }

    /** Merged booked minutes 08:00–20:00, including overlaps only once. */
    fun bookedMinutes(
        date: LocalDate,
        spans: List<D5TimeSpan>,
        zone: ZoneId,
    ): Int {
        val from =
            date
                .atTime(WINDOW_START_HOUR, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val until =
            date
                .atTime(WINDOW_END_HOUR, 0)
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
        return (millis / MILLIS_PER_MINUTE).toInt().coerceIn(0, MINUTES_PER_DAY)
    }

    fun sweep(
        date: LocalDate,
        spans: List<D5TimeSpan>,
        zone: ZoneId,
    ): Float {
        val minutes = bookedMinutes(date, spans, zone)
        return if (minutes == 0) 0f else maxOf(MIN_SWEEP_DEGREES, FULL_SWEEP_DEGREES * minutes / MINUTES_PER_DAY)
    }
}
