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
            assertFalse(views.filter { displayed(it) }.filterIsInstance<TextView>().any { it.text.contains(ACCOUNT) })
            assertFalse(views.filter { displayed(it) }.any { it.contentDescription?.contains(ACCOUNT) == true })
            val header = Views.text(root, "Account · tap to show")
            assertTrue(header.isFocusable)
            assertTrue(header.performClick())
            val accountValue = root.findViewById<TextView>(R.id.calendar_account_value)
            assertEquals(ACCOUNT, accountValue.text.toString())
            assertEquals(View.VISIBLE, accountValue.visibility)
            assertEquals("Hide account", header.text.toString())
            assertTrue(header.performClick())
            assertEquals(ACCOUNT, accountValue.text.toString())
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
            assertEquals("Calendar", Views.text(root, "Calendar").text.toString())
            assertFalse(views.filter { displayed(it) }.filterIsInstance<TextView>().any { it.text.contains(ACCOUNT) })
            assertFalse(views.filter { displayed(it) }.any { it.contentDescription?.contains(ACCOUNT) == true })
            Views.text(root, "Calendar").performClick()
            assertEquals(emptySet<Long>(), activity.selectedIds())
            Views.text(root, "Account · tap to show").performClick()
            assertEquals(ACCOUNT, root.findViewById<TextView>(R.id.calendar_account_value).text.toString())
            val fullName = Views.text(root, "Work · $ACCOUNT")
            assertEquals(View.VISIBLE, fullName.visibility)
            assertEquals("Show Work · $ACCOUNT", views.filterIsInstance<CheckBox>().single().contentDescription)
            Views.text(root, "Hide account").performClick()
            assertEquals(View.GONE, fullName.visibility)
            assertFalse(views.filter { displayed(it) }.any { it.contentDescription?.contains(ACCOUNT) == true })
        }
    }

    @Test
    fun disclosureIsScopedToOneAccountAndResetsOnViewRebuild() {
        Views.permission(Manifest.permission.READ_CALENDAR, true)
        Robolectric.buildActivity(TenSevenCalendarActivity::class.java).create().use { controller ->
            val activity = controller.get()
            val other = "other@example.invalid"
            activity.calendars =
                listOf(D5R2Calendar(1L, ACCOUNT, Color.BLUE, ACCOUNT), D5R2Calendar(2L, other, Color.RED, other))
            val actions = CalendarContentActions({}, {}, {}, {}, {}, {})
            val root = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderCalendarSelection(root)
            Views.text(root, "Account · tap to show").performClick()
            val labels = Views.descendants(root).filterIsInstance<TextView>()
            assertTrue(labels.any { displayed(it) && it.text.toString() == ACCOUNT })
            assertFalse(labels.any { displayed(it) && it.text.toString() == other })
            val rebuilt = LinearLayout(activity)
            CalendarOptionsContent(activity, actions).renderCalendarSelection(rebuilt)
            assertFalse(
                Views
                    .descendants(rebuilt)
                    .filter { displayed(it) }
                    .filterIsInstance<TextView>()
                    .any { it.text.contains(ACCOUNT) || it.text.contains(other) },
            )
            assertEquals(setOf(1L, 2L), activity.selectedIds())
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

    /** Detached test roots still have real visibility flags; do not mistake all detached views for hidden. */
    private fun displayed(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    private companion object {
        const val ACCOUNT = "owner@example.invalid"
    }
}
