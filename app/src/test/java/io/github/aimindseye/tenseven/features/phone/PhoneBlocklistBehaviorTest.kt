package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.role.RoleManager
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowRoleManager
import org.robolectric.shadows.ShadowToast
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneBlocklistBehaviorTest {
    @Test
    fun shortInputDoesNotPersistAndValidInputRequiresConfirmationToUnblock() {
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            PhoneBlocklistContent(activity).blocklist(root)
            val input = Views.input(root, "Number with country code")
            input.setText("12")
            Views.described(root, "Block number").performClick()
            assertTrue(PhoneBlocklist.blocked(activity).isEmpty())
            assertEquals("Enter a full number", ShadowToast.getTextOfLatestToast())
            input.setText(PhoneFixtureRecords.FIRST_NUMBER)
            activity.screen = TenSevenPhoneActivity.Screen.BLOCKLIST
            Views.described(root, "Block number").performClick()
            assertTrue(PhoneFixtureRecords.FIRST_NUMBER in PhoneBlocklist.blocked(activity))
            Views.described(activity.window.decorView, "Unblock ${PhoneFixtureRecords.FIRST_NUMBER}").performClick()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(PhoneFixtureRecords.FIRST_NUMBER in PhoneBlocklist.blocked(activity))
            Views.text(dialog.window!!.decorView, "Unblock").performClick()
            assertFalse(PhoneFixtureRecords.FIRST_NUMBER in PhoneBlocklist.blocked(activity))
        }
    }

    @Test
    fun allowlistRemovalAndUnknownSilencerRemainExplicitUserActions() {
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.screen = TenSevenPhoneActivity.Screen.BLOCKLIST
            activity.draw()
            var root = activity.window.decorView
            Views.input(root, "Number to allow").setText("12")
            Views.described(root, "Allow number").performClick()
            assertTrue(PhoneBlocklist.allowed(activity).isEmpty())
            Views.input(root, "Number to allow").setText(PhoneFixtureRecords.SECOND_NUMBER)
            Views.described(root, "Allow number").performClick()
            assertTrue(PhoneFixtureRecords.SECOND_NUMBER in PhoneBlocklist.allowed(activity))
            root = activity.window.decorView
            Views.described(root, "Remove allowed ${PhoneFixtureRecords.SECOND_NUMBER}").performClick()
            Views.text(ShadowDialog.getLatestDialog().window!!.decorView, "Remove").performClick()
            assertTrue(PhoneBlocklist.allowed(activity).isEmpty())
            assertFalse(PhoneBlocklist.silenceUnknown(activity))
            Views.text(activity.window.decorView, "Silence unknown: OFF").performClick()
            assertTrue(PhoneBlocklist.silenceUnknown(activity))
            Views.text(activity.window.decorView, "Silence unknown: ON").performClick()
            assertFalse(PhoneBlocklist.silenceUnknown(activity))
        }
    }

    @Test
    fun screeningRoleRequiresConsentAndHeldRoleShowsSettingsAdvice() {
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val roles = activity.getSystemService(RoleManager::class.java)
            val shadow = shadowOf(roles)
            shadow.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
            val root = FrameLayout(activity)
            PhoneBlocklistContent(activity).blocklist(root)
            assertFalse(roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
            Views.described(root, "Screening settings").performClick()
            assertEquals(
                roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING).action,
                shadowOf(activity).nextStartedActivity.action,
            )
            assertFalse(roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
            ShadowRoleManager.addRoleHolder(
                RoleManager.ROLE_CALL_SCREENING,
                activity.packageName,
                android.os.Process.myUserHandle(),
            )
            Views.described(root, "Screening settings").performClick()
            assertTrue(
                Views
                    .text(
                        ShadowDialog.getLatestDialog().window!!.decorView,
                        "Review Android Default Apps to change call screening.",
                    ).isShown,
            )
        }
    }

    @Test
    fun blockedHistoryRequestsPermissionThenDisplaysOnlyBlockedFixtures() {
        PhoneFixtureRecords.calls()
        Views.permission(Manifest.permission.READ_CALL_LOG, false)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = FrameLayout(activity)
            PhoneBlocklistContent(activity).blocklist(root)
            Views.described(root, "View blocked calls").performClick()
            assertEquals(
                listOf(Manifest.permission.READ_CALL_LOG),
                shadowOf(activity).lastRequestedPermission.requestedPermissions.toList(),
            )
            Views.permission(Manifest.permission.READ_CALL_LOG, true)
            Views.described(root, "View blocked calls").performClick()
            val body =
                Views
                    .descendants(ShadowDialog.getLatestDialog().window!!.decorView)
                    .filterIsInstance<android.widget.TextView>()
                    .map { it.text.toString() }
            assertTrue(body.any { it.contains(" · ") })
            assertFalse(body.any { it.contains(PhoneFixtureRecords.UNKNOWN_NUMBER) })
        }
    }
}
