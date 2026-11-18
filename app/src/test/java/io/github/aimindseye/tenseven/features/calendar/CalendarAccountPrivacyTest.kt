package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.graphics.Color
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import io.github.aimindseye.tenseven.calendar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarAccountPrivacyTest {
    @Test
    fun accountIdentifiersRequireExplicitRevealAndDoNotLeakThroughAccessibility() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            activity.calendars = listOf(D5R2Calendar(1L, "Work", Color.BLUE, ACCOUNT))
            val root = LinearLayout(activity)
            val actions = CalendarContentActions({}, {}, {}, {}, {}, {})
            CalendarOptionsContent(activity, actions).renderCalendarSelection(root)
            val views = Views.descendants(root)
            assertFalse(views.filterIsInstance<TextView>().any { it.text.contains(ACCOUNT) })
            assertFalse(views.any { it.contentDescription?.contains(ACCOUNT) == true })
            val header = Views.text(root, "Account · tap to show")
            assertTrue(header.isFocusable)
            assertTrue(header.performClick())
            val accountValue = root.findViewById<TextView>(R.id.calendar_account_value)
            assertEquals(ACCOUNT, accountValue.text.toString())
            assertEquals(View.VISIBLE, accountValue.visibility)
            assertEquals("Hide account", header.text.toString())
            assertTrue(header.performClick())
            assertEquals("", accountValue.text.toString())
            assertEquals(View.GONE, accountValue.visibility)
            assertEquals("Account · tap to show", header.text.toString())
            val toggle = views.filterIsInstance<CheckBox>().single()
            assertTrue(toggle.isChecked)
            toggle.isChecked = false
            assertEquals(emptySet<Long>(), activity.selectedIds())
        }
    }

    @Test
    fun accountAliasesInCalendarNamesAndAccessibilityStayPrivateUntilReveal() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            activity.calendars = listOf(D5R2Calendar(1L, "Work · $ACCOUNT", Color.BLUE, ACCOUNT))
            val root = LinearLayout(activity)
            val actions = CalendarContentActions({}, {}, {}, {}, {}, {})
            CalendarOptionsContent(activity, actions).renderCalendarSelection(root)
            val views = Views.descendants(root)
            assertEquals("Work · Account", Views.text(root, "Work · Account").text.toString())
            assertFalse(views.filterIsInstance<TextView>().any { it.text.contains(ACCOUNT) })
            assertFalse(views.any { it.contentDescription?.contains(ACCOUNT) == true })
            Views.text(root, "Work · Account").performClick()
            assertEquals(emptySet<Long>(), activity.selectedIds())
            Views.text(root, "Account · tap to show").performClick()
            assertEquals(ACCOUNT, root.findViewById<TextView>(R.id.calendar_account_value).text.toString())
        }
    }

    @Test
    fun localCalendarLabelDoesNotPretendToHideAnIdentifier() {
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val root = LinearLayout(activity)
            activity.views.accountHeader(root, "On this device")
            assertFalse(Views.text(root, "On this device").performClick())
        }
    }

    private companion object {
        const val ACCOUNT = "owner@example.invalid"
    }
}
