package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.app.DatePickerDialog
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
import java.time.ZoneId
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarNavigationBehaviorTest {
    @Test
    fun weekMonthDayAndTodayNavigateUsingTheOwnedActivityDate() {
        Views.permission(Manifest.permission.READ_CALENDAR, false)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.selected = LocalDate.parse("2026-01-31")
            activity.month = YearMonth.from(activity.selected)
            Views.text(activity.window.decorView, "Month").performClick()
            Views.described(activity.window.decorView, "Next month").performClick()
            assertEquals(LocalDate.parse("2026-02-28"), activity.selected)
            Views.text(activity.window.decorView, "Week").performClick()
            Views.described(activity.window.decorView, "Next week").performClick()
            assertEquals(LocalDate.parse("2026-03-07"), activity.selected)
            Views.text(activity.window.decorView, "Day").performClick()
            Views.described(activity.window.decorView, "Previous day").performClick()
            assertEquals(LocalDate.parse("2026-03-06"), activity.selected)
            Views.text(activity.window.decorView, "Today").performClick()
            assertEquals(LocalDate.now(activity.zone), activity.selected)
            assertEquals(YearMonth.from(activity.selected), activity.month)
        }
    }

    @Test
    fun datePickerSelectionUpdatesTheOwnedDateAndMonth() {
        Views.permission(Manifest.permission.READ_CALENDAR, false)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val heading =
                Views
                    .descendants(activity.window.decorView)
                    .filterIsInstance<android.widget.TextView>()
                    .first { it.text.startsWith("Today  ·  ") }
            heading.performClick()
            val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
            picker.datePicker.updateDate(2028, 1, 29)
            // Robolectric captures the platform listener instead of running the native dialog callback.
            shadowOf(picker).onDateSetListenerCallback.onDateSet(picker.datePicker, 2028, 1, 29)
            assertEquals(LocalDate.parse("2028-02-29"), activity.selected)
            assertEquals(YearMonth.of(2028, 2), activity.month)
        }
    }

    @Test
    fun springAndFallInstancesUseLocalMidnightIncludingTheDstHour() {
        val zone = ZoneId.of("America/New_York")
        val spring = LocalDate.parse("2026-03-08")
        val fall = LocalDate.parse("2026-11-01")
        val springHours =
            java.time.Duration
                .between(
                    spring.atStartOfDay(zone),
                    spring.plusDays(1).atStartOfDay(zone),
                ).toHours()
        val fallHours =
            java.time.Duration
                .between(
                    fall.atStartOfDay(zone),
                    fall.plusDays(1).atStartOfDay(zone),
                ).toHours()
        assertEquals(23L, springHours)
        assertEquals(25L, fallHours)
        listOf(spring, fall).forEach { day ->
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end =
                day
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val instance = D5R2Event(1L, 1L, "Fixture DST", start, end, false)
            assertEquals(listOf(instance), calendarEventsOn(listOf(instance), day, zone))
            assertTrue(calendarEventsOn(listOf(instance), day.plusDays(1), zone).isEmpty())
        }
    }
}
