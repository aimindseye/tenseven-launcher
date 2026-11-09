package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.github.aimindseye.tenseven.model.D5TimeSpan
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.ZoneId

data class D5CalendarSource(val id: Long, val name: String, val account: String)
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
    fun overlaps(day: LocalDate, zone: ZoneId): Boolean {
        // Android's all-day instances use UTC date boundaries, not local wall-clock midnight.
        if (allDay) {
            val first = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val end = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
            return !day.isBefore(first) && day.isBefore(end)
        }
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return startMillis < to && endMillis > from
    }
}

/** Android CalendarProvider: account-agnostic, no syncing, no copy of user events to disk. */
class D5CalendarRepository(private val context: Context) {
    fun permitted() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun sources(): List<D5CalendarSource> {
        if (!permitted()) return emptyList()
        return runCatching {
            val results = mutableListOf<D5CalendarSource>()
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME),
                "${CalendarContract.Calendars.VISIBLE}=1", null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    results += D5CalendarSource(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Calendar" }, c.getString(2).orEmpty())
                }
            }
            results
        }.getOrDefault(emptyList())
    }

    fun events(month: YearMonth, selected: Set<Long>?, visibleSources: Set<Long>): List<D5CalendarEvent> {
        if (!permitted() || visibleSources.isEmpty()) return emptyList()
        val z = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        return runCatching {
            val events = mutableListOf<D5CalendarEvent>()
            context.contentResolver.query(
                builder.build(),
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME),
                null, null, CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext() && events.size < 1500) {
                    val calendarId = c.getLong(5)
                    if (calendarId !in visibleSources || selected != null && calendarId !in selected) continue
                    val start = c.getLong(2)
                    val end = c.getLong(3)
                    if (end <= start) continue
                    events += D5CalendarEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Untitled event" },
                        start, end, c.getInt(4) != 0, calendarId,
                        c.getString(6).orEmpty().ifBlank { "Calendar" })
                }
            }
            events
        }.getOrDefault(emptyList())
    }
}
