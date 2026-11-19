package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.widget.FrameLayout
import android.widget.TextView
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
class PhoneRecentsBehaviorTest {
    @Test
    fun deniedHistoryRetainsVoicemailAndExplicitPermissionAndRoleActions() {
        Views.permission(Manifest.permission.READ_CALL_LOG, false)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            val callbacks = mutableListOf<String>()
            PhoneRecentsContent(activity, { error("Unexpected contact") }, { callbacks.add("voicemail") }, {
                callbacks.add("role")
            }).recents(root)
            Views.text(root, "Open default Phone setup").performClick()
            Views.described(root, "Open voicemail").performClick()
            assertEquals(listOf("role", "voicemail"), callbacks)
            Views.text(root, "Request call-log permission").performClick()
            assertEquals(
                listOf(Manifest.permission.READ_CALL_LOG),
                shadowOf(activity).lastRequestedPermission.requestedPermissions.toList(),
            )
            Views.described(root, "Open dialpad").performClick()
            assertEquals(TenSevenPhoneActivity.Screen.DIAL, activity.screen)
        }
    }

    @Test
    fun historyDistinguishesKnownAndUnknownCallersAndRoutesRowActions() {
        PhoneFixtureRecords.contacts()
        PhoneFixtureRecords.calls()
        Views.permission(Manifest.permission.READ_CONTACTS, true)
        Views.permission(Manifest.permission.READ_CALL_LOG, true)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            var selected: Long? = null
            PhoneRecentsContent(activity, { selected = it }, {}, {}).recents(root)
            assertEquals("Alex Fixture", Views.text(root, "Alex Fixture").text.toString())
            assertEquals("Private number", Views.text(root, "Private number").text.toString())
            Views
                .descendants(
                    root,
                ).first { it.contentDescription?.startsWith("Alex Fixture, Missed") == true }
                .performClick()
            assertEquals(PhoneFixtureRecords.FIRST_ID, selected)
            Views
                .descendants(root)
                .first {
                    it.contentDescription?.startsWith("${PhoneFixtureRecords.UNKNOWN_NUMBER}, Outgoing") == true
                }.performClick()
            assertEquals(PhoneFixtureRecords.UNKNOWN_NUMBER, activity.dialNumber)
            assertEquals(TenSevenPhoneActivity.Screen.DIAL, activity.screen)
        }
    }

    @Test
    fun eachFilterRestrictsRowsAndFilterClickPersistsAcrossRedraw() {
        PhoneFixtureRecords.contacts()
        PhoneFixtureRecords.calls()
        Views.permission(Manifest.permission.READ_CONTACTS, true)
        Views.permission(Manifest.permission.READ_CALL_LOG, true)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val expected =
                mapOf(
                    "Missed" to "Alex Fixture",
                    "Incoming" to "Blair Fixture",
                    "Outgoing" to PhoneFixtureRecords.UNKNOWN_NUMBER,
                )
            expected.forEach { (filter, name) ->
                activity.callFilter = filter
                val root = FrameLayout(activity)
                PhoneRecentsContent(activity, {}, {}, {}).recents(root)
                assertEquals(name, Views.text(root, name).text.toString())
                assertFalse(Views.descendants(root).any { it.contentDescription?.startsWith(", Blocked") == true })
            }
            activity.callFilter = "Favorites"
            val favorites = FrameLayout(activity)
            PhoneRecentsContent(activity, {}, {}, {}).recents(favorites)
            assertEquals("Alex Fixture", Views.text(favorites, "Alex Fixture").text.toString())
            assertFalse(Views.descendants(favorites).filterIsInstance<TextView>().any { it.text == "Blair Fixture" })
            activity.screen = TenSevenPhoneActivity.Screen.RECENTS
            Views.described(favorites, "Show Incoming calls").performClick()
            assertEquals("Incoming", activity.callFilter)
            assertTrue(Views.text(activity.window.decorView, "Blair Fixture").isShown)
        }
    }
}
