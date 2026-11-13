package io.github.aimindseye.tenseven.features.calendar

import io.github.aimindseye.tenseven.model.D5TimeSpan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class D5CalendarSource(
    val id: Long,
    val name: String,
    val account: String,
)

data class D5CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val calendarName: String,
) {
    fun timeSpan() = D5TimeSpan(startMillis, endMillis, allDay)

    fun overlaps(
        day: LocalDate,
        zone: ZoneId,
    ): Boolean {
        // Android's all-day instances use UTC date boundaries, not local wall-clock midnight.
        if (allDay) {
            val first = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val end = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
            return !day.isBefore(first) && day.isBefore(end)
        }
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to =
            day
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        return startMillis < to && endMillis > from
    }
}
