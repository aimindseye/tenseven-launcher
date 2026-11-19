package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import java.time.YearMonth
import java.time.ZoneId

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val CALENDAR_REPO_EVENTS_COL_3 = 3
private const val CALENDAR_REPO_EVENTS_COL_4 = 4
private const val CALENDAR_REPO_EVENTS_COL_5 = 5
private const val CALENDAR_REPO_EVENTS_MAX_1500 = 1500
private const val CALENDAR_REPO_EVENTS_VALUE_6 = 6

/** Android CalendarProvider: account-agnostic, no syncing, no copy of user events to disk. */
class D5CalendarRepository(
    private val context: Context,
) {
    fun permitted() =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager
                .PERMISSION_GRANTED

    fun sources(): List<D5CalendarSource> {
        if (!permitted()) return emptyList()
        return runCatching {
            val results = mutableListOf<D5CalendarSource>()
            context.contentResolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                    ),
                    "${CalendarContract.Calendars.VISIBLE}=1",
                    null,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        results +=
                            D5CalendarSource(
                                c.getLong(0),
                                c.getString(1).orEmpty().ifBlank { "Calendar" },
                                c.getString(2).orEmpty(),
                            )
                    }
                }
            results
        }.getOrDefault(emptyList())
    }

    private fun visibleEvent(
        cursor: Cursor,
        selected: Set<Long>?,
        visibleSources: Set<Long>,
    ): D5CalendarEvent? {
        val calendarId = cursor.getLong(CALENDAR_REPO_EVENTS_COL_5)
        val visible = calendarId in visibleSources && (selected == null || calendarId in selected)
        val start = cursor.getLong(2)
        val end = cursor.getLong(CALENDAR_REPO_EVENTS_COL_3)
        return if (visible && end > start) {
            D5CalendarEvent(
                cursor.getLong(0),
                cursor.getString(1).orEmpty().ifBlank { "Untitled event" },
                start,
                end,
                cursor.getInt(CALENDAR_REPO_EVENTS_COL_4) != 0,
                calendarId,
                cursor.getString(CALENDAR_REPO_EVENTS_VALUE_6).orEmpty().ifBlank { "Calendar" },
            )
        } else {
            null
        }
    }

    fun events(
        month: YearMonth,
        selected: Set<Long>?,
        visibleSources: Set<Long>,
    ): List<D5CalendarEvent> {
        if (!permitted() || visibleSources.isEmpty()) return emptyList()
        val z = ZoneId.systemDefault()
        val from =
            month
                .atDay(1)
                .atStartOfDay(z)
                .toInstant()
                .toEpochMilli()
        val to =
            month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(z)
                .toInstant()
                .toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        return runCatching {
            val events = mutableListOf<D5CalendarEvent>()
            context.contentResolver
                .query(
                    builder.build(),
                    arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                        CalendarContract.Instances.CALENDAR_ID,
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    ),
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC",
                )?.use { c ->
                    while (c.moveToNext() && events.size < CALENDAR_REPO_EVENTS_MAX_1500) {
                        visibleEvent(c, selected, visibleSources)?.let(events::add)
                    }
                }
            events
        }.getOrDefault(emptyList())
    }
}
