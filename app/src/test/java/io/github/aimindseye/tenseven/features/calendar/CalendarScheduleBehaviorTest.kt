package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.graphics.Color
import android.provider.CalendarContract
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate
import java.time.YearMonth
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class CalendarScheduleBehaviorTest {
    @Test
    fun weekAndAgendaRowsPreserveProviderInstancesAndSelectionCallback() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        val provider = Views.provider(CalendarContract.AUTHORITY)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val date = LocalDate.parse("2026-11-01")
            activity.selected = date
            activity.month = YearMonth.from(date)
            provider.records["/calendars"] = listOf(calendar())
            provider.records["/instances/when"] =
                listOf(event(activity, date, false), event(activity, date.plusDays(1), true))
            controller.start().resume()
            awaitEvents(activity, date)
            assertEquals(listOf("Fixture recurrence"), activity.eventsOn(date).map { it.title })
            assertEquals(listOf("Fixture recurrence"), activity.eventsOn(date.plusDays(1)).map { it.title })
            var selected: LocalDate? = null
            val content = CalendarScheduleContent(activity, actions { selected = it })
            val root = LinearLayout(activity)
            content.renderWeekPage(root)
            Views.described(root, "Select MONDAY 2").performClick()
            assertEquals(date.plusDays(1), selected)
            val eventRow =
                Views
                    .descendants(root)
                    .filterIsInstance<android.widget.TextView>()
                    .first { it.text.contains("Fixture recurrence") }
            (eventRow.parent.parent as android.view.View).performClick()
            assertTrue(Views.text(ShadowDialog.getLatestDialog().window!!.decorView, "Fixture recurrence").isShown)
            assertTrue(provider.queries.any { it.path?.startsWith("/instances/when/") == true })
        }
    }

    @Test
    fun monthAndDayRenderSelectionAllDayAndTimelineWithoutChangingActivityDate() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        val provider = Views.provider(CalendarContract.AUTHORITY)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val day = LocalDate.parse("2026-11-01")
            activity.selected = day
            activity.month = YearMonth.from(day)
            provider.records["/calendars"] = listOf(calendar())
            provider.records["/instances/when"] = listOf(event(activity, day, true), event(activity, day, false))
            controller.start().resume()
            awaitEvents(activity, day)
            val content = CalendarScheduleContent(activity, actions {})
            val month = LinearLayout(activity)
            content.renderMonthPage(month)
            assertEquals(1, Views.descendants(month).filterIsInstance<BusyMonthView>().size)
            val timeline = LinearLayout(activity)
            content.renderDayPage(timeline)
            assertEquals(
                "ALL DAY  Fixture recurrence",
                Views.text(timeline, "ALL DAY  Fixture recurrence").text.toString(),
            )
            assertEquals(1, Views.descendants(timeline).filterIsInstance<D5DayTimelineView>().size)
            assertEquals(day, activity.selected)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun compactEmptyViewsKeepNavigationAndSelectedDate() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            activity.selected = LocalDate.parse("2026-03-08")
            activity.month = YearMonth.from(activity.selected)
            val content = CalendarScheduleContent(activity, actions {})
            val root = LinearLayout(activity)
            content.renderMonthPage(root)
            assertEquals(
                "No events today in the selected calendars",
                Views.text(root, "No events today in the selected calendars").text.toString(),
            )
            val day = LinearLayout(activity)
            content.renderDayPage(day)
            assertTrue(Views.descendants(day).any { it is D5DayTimelineView })
            assertEquals(LocalDate.parse("2026-03-08"), activity.selected)
        }
    }

    @Test
    fun regrantAndEditorReturnRefreshProviderStateAndRevocationClearsIt() {
        Views.permission(Manifest.permission.READ_CALENDAR, false)
        val provider = Views.provider(CalendarContract.AUTHORITY)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val day = activity.selected
            provider.records["/calendars"] = listOf(calendar())
            provider.records["/instances/when"] = listOf(event(activity, day, false))
            assertTrue(provider.queries.isEmpty())
            Views.permission(Manifest.permission.READ_CALENDAR, true)
            activity.onRequestPermissionsResult(
                CALENDAR_CALENDAR_SELECTION_REQUEST_221,
                arrayOf(Manifest.permission.READ_CALENDAR),
                intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED),
            )
            awaitEvents(activity, day)
            assertEquals("Fixture recurrence", activity.eventsOn(day).single().title)
            provider.records["/instances/when"] =
                listOf(
                    event(activity, day, false) +
                        (CalendarContract.Instances.TITLE to "Edited fixture"),
                )
            controller.pause().resume()
            awaitTitle(activity, day, "Edited fixture")
            assertTrue(
                Views
                    .descendants(activity.window.decorView)
                    .filterIsInstance<android.widget.TextView>()
                    .any { it.text.contains("Edited fixture") },
            )
            Views.permission(Manifest.permission.READ_CALENDAR, false)
            controller.pause().resume()
            assertTrue(activity.eventsOn(day).isEmpty())
            assertTrue(activity.calendars.isEmpty())
        }
    }

    private fun awaitTitle(
        activity: TenSevenCalendarActivity,
        day: LocalDate,
        title: String,
    ) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (activity.eventsOn(day).none { it.title == title } && System.nanoTime() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(title, activity.eventsOn(day).single().title)
    }

    private fun actions(select: (LocalDate) -> Unit) = CalendarContentActions(select, {}, {}, {}, {}, {})

    private fun calendar(): Map<String, Any> =
        mapOf(
            CalendarContract.Calendars._ID to 1L,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "Fixture calendar",
            CalendarContract.Calendars.CALENDAR_COLOR to Color.BLUE,
            CalendarContract.Calendars.ACCOUNT_NAME to "Fixture account",
        )

    private fun event(
        activity: TenSevenCalendarActivity,
        day: LocalDate,
        allDay: Boolean,
    ): Map<String, Any> =
        mapOf(
            CalendarContract.Instances.EVENT_ID to 1L,
            CalendarContract.Instances.CALENDAR_ID to 1L,
            CalendarContract.Instances.TITLE to "Fixture recurrence",
            CalendarContract.Instances.BEGIN to activity.dateMs(day),
            CalendarContract.Instances.END to activity.dateMs(day.plusDays(1)),
            CalendarContract.Instances.ALL_DAY to if (allDay) 1 else 0,
        )

    private fun awaitEvents(
        activity: TenSevenCalendarActivity,
        day: LocalDate,
    ) {
        val deadline =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS
                    .toNanos(5)
        while (activity.eventsOn(day).isEmpty() && System.nanoTime() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Provider result must reach the Activity", activity.eventsOn(day).isNotEmpty())
    }
}
