package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarViewBehaviorTest {
    @Test
    fun calendarAccessTracksPermissionDenialAndRegrant() {
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val application = shadowOf(activity.application)
            application.denyPermissions(Manifest.permission.READ_CALENDAR)
            assertFalse(activity.views.permission())
            application.grantPermissions(Manifest.permission.READ_CALENDAR)
            assertTrue(activity.views.permission())
        }
    }

    @Test
    fun calendarActionKeepsItsLabelAndInvokesOnlyOnClick() {
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            var calls = 0
            val action = activity.views.pill("Today", active = true) { calls++ }
            assertEquals("Today", action.text.toString())
            assertTrue(action.isFocusable)
            assertEquals(0, calls)
            assertTrue(action.performClick())
            assertEquals(1, calls)
        }
    }
}
