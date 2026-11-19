package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowRoleManager
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

/** Telecom is Robolectric's in-memory shadow; these tests cannot place device calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhonePermissionBehaviorTest {
    private val callPermissionRequest = 203

    @Test
    fun queuedCallWaitsForPermissionAndConsumesNumberOnlyOnce() {
        Views.permission(Manifest.permission.CALL_PHONE, false)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            ShadowRoleManager.addRoleHolder(
                RoleManager.ROLE_DIALER,
                activity.packageName,
                android.os.Process.myUserHandle(),
            )
            val telecom = shadowOf(activity.getSystemService(TelecomManager::class.java))
            activity.placeCall(PhoneFixtureRecords.FIRST_NUMBER)
            assertTrue(telecom.allOutgoingCalls.isEmpty())
            assertEquals(callPermissionRequest, shadowOf(activity).lastRequestedPermission.requestCode)
            activity.onRequestPermissionsResult(
                callPermissionRequest,
                arrayOf(Manifest.permission.CALL_PHONE),
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
            assertTrue(telecom.allOutgoingCalls.isEmpty())
            Views.permission(Manifest.permission.CALL_PHONE, true)
            activity.onRequestPermissionsResult(
                callPermissionRequest,
                arrayOf(Manifest.permission.CALL_PHONE),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
            assertEquals(1, telecom.allOutgoingCalls.size)
            activity.onRequestPermissionsResult(
                callPermissionRequest,
                arrayOf(Manifest.permission.CALL_PHONE),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
            assertEquals(1, telecom.allOutgoingCalls.size)
        }
    }

    @Test
    fun nonDefaultPhoneNeverAcquiresRoleOrInitiatesTelecomCall() {
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val roles = activity.getSystemService(RoleManager::class.java)
            val telecom = shadowOf(activity.getSystemService(TelecomManager::class.java))
            assertFalse(roles.isRoleHeld(RoleManager.ROLE_DIALER))
            activity.placeCall(PhoneFixtureRecords.FIRST_NUMBER)
            assertTrue(telecom.allOutgoingCalls.isEmpty())
            assertFalse(roles.isRoleHeld(RoleManager.ROLE_DIALER))
            assertEquals(null, shadowOf(activity).lastRequestedPermission)
        }
    }
}
