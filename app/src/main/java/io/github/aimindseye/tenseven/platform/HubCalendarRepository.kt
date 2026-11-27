package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract

private object CalendarQueryLimits {
    const val UPCOMING_DAYS = 7L
    const val HOURS_PER_DAY = 24L
    const val MINUTES_PER_HOUR = 60L
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1000L
    const val UPCOMING_WINDOW_MS =
        UPCOMING_DAYS * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    const val MAX_UPCOMING_ROWS = 20
    const val DEFAULT_DISPLAY_ROWS = 3
    const val INSTANCE_CALENDAR_ID_INDEX = 5
    const val INSTANCE_ALL_DAY_INDEX = 3
    const val INSTANCE_CALENDAR_NAME_INDEX = 4
}

/** Read-only, on-demand events from *all* calendars exposed by Android CalendarProvider.
 * Multiple synced account calendars are supported; apps not syncing with this provider are not.
 */
data class HubCalendarEvent(
    val title: String,
    val beginsAt: Long,
    val endsAt: Long,
    val calendarName: String,
    val allDay: Boolean,
    val calendarId: Long = 0L,
)

data class HubCalendarSource(
    val id: Long,
    val name: String,
    val account: String,
)

class HubCalendarRepository(
    private val context: Context,
) {
    /** Accessible, visible device calendars. IDs are only stable on this device. */
    fun calendars(): List<HubCalendarSource> {
        if (!hasPermission()) return emptyList()
        return try {
            val rows = mutableListOf<HubCalendarSource>()
            context.contentResolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                    ),
                    CalendarContract.Calendars.VISIBLE + " = 1",
                    null,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        rows +=
                            HubCalendarSource(
                                cursor.getLong(0),
                                cursor.getString(1).orEmpty().ifBlank { "Calendar" },
                                cursor.getString(2).orEmpty(),
                            )
                    }
                }
            rows
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager
                .PERMISSION_GRANTED

    fun upcoming(
        now: Long = System.currentTimeMillis(),
        selectedIds: Set<Long>? = null,
    ): List<HubCalendarEvent> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + CalendarQueryLimits.UPCOMING_WINDOW_MS)
        return try {
            val found = mutableListOf<HubCalendarEvent>()
            context.contentResolver
                .query(
                    uri.build(),
                    arrayOf(
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Instances.CALENDAR_ID,
                    ),
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC",
                )?.use { cursor ->
                    // On-device calendar data only; no persistent cache and no network access.
                    while (cursor.moveToNext() && found.size < CalendarQueryLimits.MAX_UPCOMING_ROWS) {
                        val ends = cursor.getLong(2)
                        if (ends <= now ||
                            (
                                selectedIds != null && cursor.getLong(
                                    CalendarQueryLimits
                                        .INSTANCE_CALENDAR_ID_INDEX,
                                ) !in selectedIds
                            )
                        ) {
                            continue
                        }
                        found +=
                            HubCalendarEvent(
                                title = cursor.getString(0).orEmpty().ifBlank { "Calendar event" },
                                beginsAt = cursor.getLong(1),
                                endsAt = ends,
                                allDay = cursor.getInt(CalendarQueryLimits.INSTANCE_ALL_DAY_INDEX) != 0,
                                calendarName =
                                    cursor
                                        .getString(
                                            CalendarQueryLimits.INSTANCE_CALENDAR_NAME_INDEX,
                                        ).orEmpty()
                                        .ifBlank { "Calendar" },
                                calendarId = cursor.getLong(CalendarQueryLimits.INSTANCE_CALENDAR_ID_INDEX),
                            )
                    }
                }
            HubCalendarPolicy.next(found)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }
}

object HubCalendarPolicy {
    fun next(
        rows: List<HubCalendarEvent>,
        max: Int = CalendarQueryLimits.DEFAULT_DISPLAY_ROWS,
    ): List<HubCalendarEvent> =
        rows
            .distinctBy { Triple(it.title, it.beginsAt, it.calendarId to it.calendarName) }
            .sortedWith(compareBy({ it.beginsAt }, { it.calendarName }))
            .take(max.coerceAtLeast(0))
}
