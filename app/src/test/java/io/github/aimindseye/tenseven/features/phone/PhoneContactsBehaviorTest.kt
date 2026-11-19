package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.widget.FrameLayout
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
class PhoneContactsBehaviorTest {
    @Test
    fun deniedContactsRequestsReadAccessWithoutQueryingProvider() {
        val provider = PhoneFixtureRecords.contacts()
        Views.permission(Manifest.permission.READ_CONTACTS, false)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            PhoneContactsContent(
                activity,
                { error("Unexpected contact") },
                { error("Unexpected editor") },
            ).contacts(root)
            Views.text(root, "Allow Contacts").performClick()
            assertEquals(
                listOf(Manifest.permission.READ_CONTACTS),
                shadowOf(activity).lastRequestedPermission.requestedPermissions.toList(),
            )
            assertTrue(provider.queries.isEmpty())
        }
    }

    @Test
    fun searchReplacesRowsKeepsFocusAndRoutesContactAndEditorCallbacks() {
        PhoneFixtureRecords.contacts()
        Views.permission(Manifest.permission.READ_CONTACTS, true)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            var selected: Long? = null
            var adds = 0
            PhoneContactsContent(activity, { selected = it }, { adds++ }).contacts(root)
            activity.setContentView(root)
            val search = Views.input(root, "⌕  Search contacts")
            assertTrue(search.requestFocus())
            search.setText("Alex")
            assertEquals("Alex", activity.contactSearch)
            assertTrue(search.hasFocus())
            assertFalse(Views.descendants(root).any { it.contentDescription == "Open Blair Fixture" })
            Views.described(root, "Open Alex Fixture").performClick()
            assertEquals(PhoneFixtureRecords.FIRST_ID, selected)
            Views.described(root, "Jump to A").performClick()
            Views.described(root, "Jump to Z").performClick()
            search.setText("no such fixture")
            assertEquals("No matching contacts", Views.text(root, "No matching contacts").text.toString())
            search.setText("")
            assertTrue(Views.described(root, "Open Blair Fixture").isFocusable)
            Views.described(root, "Add contact").performClick()
            assertEquals(1, adds)
        }
    }

    @Test
    fun editorReturnAndRecreationRetainSearchWithoutRetainingOldViews() {
        PhoneFixtureRecords.contacts()
        Views.permission(Manifest.permission.READ_CONTACTS, true)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.screen = TenSevenPhoneActivity.Screen.CONTACTS
            activity.draw()
            val search = Views.input(activity.window.decorView, "⌕  Search contacts")
            search.setText("Blair")
            controller.pause().resume()
            assertEquals("Blair", Views.input(activity.window.decorView, "⌕  Search contacts").text.toString())
            controller.recreate()
            val recreated = controller.get()
            assertEquals(TenSevenPhoneActivity.Screen.CONTACTS, recreated.screen)
            assertEquals("Blair", recreated.contactSearch)
            assertEquals("Blair", Views.input(recreated.window.decorView, "⌕  Search contacts").text.toString())
        }
    }
}
