package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.provider.CalendarContract
import android.widget.CheckBox
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarOptionsBehaviorTest {
    private val requests = mutableListOf<String>()
    private val actions =
        CalendarContentActions(
            { requests.add(it.toString()) },
            { requests.add(it.toString()) },
            { requests.add(it) },
            { requests.add("load") },
            { requests.add("return") },
            { requests.add("render") },
        )

    @Test
    fun deniedSelectionRequestsOnlyReadPermission() {
        Views.permission(Manifest.permission.READ_CALENDAR, false)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val root = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderCalendarSelection(root)
            assertEquals(
                android.view.View.VISIBLE,
                Views.text(root, "Allow Calendar access to select your calendars.").visibility,
            )
            Views.text(root, "Allow Calendars").performClick()
            val request = shadowOf(activity).lastRequestedPermission
            assertEquals(listOf(Manifest.permission.READ_CALENDAR), request.requestedPermissions.toList())
            assertEquals(CALENDAR_CALENDAR_SELECTION_REQUEST_221, request.requestCode)
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun selectionPersistsTogglesAndDoneRefreshesBeforeReturning() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            activity.calendars = listOf(D5R2Calendar(1L, "Fixture calendar", Color.BLUE, ""))
            val root = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderCalendarSelection(root)
            val toggle = Views.descendants(root).filterIsInstance<CheckBox>().single()
            assertTrue(toggle.isChecked)
            Views.text(root, "Fixture calendar").performClick()
            assertFalse(toggle.isChecked)
            assertEquals(emptySet<Long>(), activity.selectedIds())
            toggle.isChecked = true
            assertEquals(setOf(1L), activity.selectedIds())
            Views.text(root, "Done").performClick()
            assertEquals(listOf("load", "return"), requests)
        }
    }

    @Test
    fun weekPreferenceChangesOnlyOnClickAndAccountsRoutesToSelection() {
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val root = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderSettings(root)
            assertFalse(activity.mondayStart())
            Views
                .descendants(root)
                .filterIsInstance<android.widget.TextView>()
                .first { it.text.startsWith("Week starts") }
                .performClick()
            assertTrue(activity.mondayStart())
            assertEquals(listOf("render"), requests)
            Views.text(root, "Calendars & accounts   ›").performClick()
            assertEquals(listOf("render", "Calendars"), requests)
        }
    }

    @Test
    fun editorHandoffKeepsTrimmedFieldsAndSelectedCalendarWithoutWritingProvider() {
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            activity.calendars = listOf(D5R2Calendar(1L, "Fixture calendar", Color.BLUE, "Fixture"))
            val root = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderEventComposer(root)
            Views.input(root, "Event title").setText("  Fixture event  ")
            Views.input(root, "Location").setText("  Fixture room  ")
            Views.input(root, "Notes (optional)").setText("  Fixture notes  ")
            Views.text(root, "Continue to Android Calendar").performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_INSERT, intent.action)
            assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
            assertEquals("Fixture event", intent.getStringExtra(CalendarContract.Events.TITLE))
            assertEquals("Fixture room", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
            assertEquals("Fixture notes", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
            assertEquals(1L, intent.getLongExtra(CalendarContract.Events.CALENDAR_ID, -1L))
            assertTrue(requests.isEmpty())
        }
    }
}
