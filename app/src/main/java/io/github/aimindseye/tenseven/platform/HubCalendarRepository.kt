package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract

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

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun upcoming(
        now: Long = System.currentTimeMillis(),
        selectedIds: Set<Long>? = null,
    ): List<HubCalendarEvent> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + 7L * 24L * 60L * 60L * 1000L)
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
                    while (cursor.moveToNext() && found.size < 20) {
                        val ends = cursor.getLong(2)
                        if (ends <= now || (selectedIds != null && cursor.getLong(5) !in selectedIds)) continue
                        found +=
                            HubCalendarEvent(
                                title = cursor.getString(0).orEmpty().ifBlank { "Calendar event" },
                                beginsAt = cursor.getLong(1),
                                endsAt = ends,
                                allDay = cursor.getInt(3) != 0,
                                calendarName = cursor.getString(4).orEmpty().ifBlank { "Calendar" },
                                calendarId = cursor.getLong(5),
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
        max: Int = 3,
    ): List<HubCalendarEvent> =
        rows
            .distinctBy { Triple(it.title, it.beginsAt, it.calendarId to it.calendarName) }
            .sortedWith(compareBy({ it.beginsAt }, { it.calendarName }))
            .take(max.coerceAtLeast(0))
}
