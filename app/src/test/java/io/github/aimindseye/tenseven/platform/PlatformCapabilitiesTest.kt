package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformCapabilitiesTest {
    @Test fun permissionAndLockSnapshotsChangeIndependentlyOnReturn() {
        val denied = PlatformCapabilities.evaluate(true, false, false, false, false, false)
        assertEquals(CapabilityStatus.PermissionRequired, denied.contacts)
        assertEquals(CapabilityStatus.PermissionRequired, denied.photos)
        assertEquals(CapabilityStatus.Unavailable, denied.camera)
        val ready =
            PlatformCapabilities.evaluate(
                true,
                true,
                true,
                true,
                true,
                false,
                FramePlatformAccess(
                    contacts = true,
                    photos = true,
                    camera = true,
                    secureKeyguard = true,
                    deviceLocked = true,
                    lockService = true,
                ),
            )
        assertEquals(CapabilityStatus.Ready, ready.contacts)
        assertEquals(CapabilityStatus.Ready, ready.photos)
        assertEquals(CapabilityStatus.Ready, ready.camera)
        org.junit.Assert.assertTrue(ready.secureKeyguard && ready.deviceLocked && ready.lockServiceAvailable)
        assertEquals(CapabilityStatus.Unavailable, ready.thirdPartySnapshots)
        org.junit.Assert.assertFalse(denied.deviceLocked)
    }

    @Test fun ordinaryInstallNeverPromisesForeignAppSnapshots() {
        val caps =
            PlatformCapabilities.evaluate(
                hasListenerService = true,
                listenerAllowed = false,
                usageAllowed = true,
                calendarAllowed = false,
                overlayAllowed = false,
                trustedSnapshotBridge = false,
            )
        assertEquals(CapabilityStatus.PermissionRequired, caps.hubNotifications)
        assertEquals(CapabilityStatus.Ready, caps.usageRecents)
        assertEquals(CapabilityStatus.Unavailable, caps.thirdPartySnapshots)
        assertEquals(CapabilityStatus.Ready, caps.localTasks)
    }

    @Test fun missingServiceIsNotMistakenForPermissionRequired() {
        val caps =
            PlatformCapabilities.evaluate(
                hasListenerService = false,
                listenerAllowed = true,
                usageAllowed = false,
                calendarAllowed = true,
                overlayAllowed = true,
                trustedSnapshotBridge = false,
            )
        assertEquals(CapabilityStatus.Unavailable, caps.hubNotifications)
        assertEquals(CapabilityStatus.Ready, caps.calendarEvents)
        assertEquals(CapabilityStatus.Ready, caps.localTasks)
    }

    @Test fun taskAndDeviceCapabilitiesAreIndependentAndDetected() {
        val caps =
            PlatformCapabilities.evaluate(
                true,
                true,
                true,
                true,
                true,
                true,
                FramePlatformAccess(
                    tasks = true,
                    resume = true,
                    removal = false,
                    keyboard = true,
                    lock = CapabilityStatus.PermissionRequired,
                    postNotifications = false,
                    widgets = false,
                ),
            )
        assertEquals(CapabilityStatus.Ready, caps.thirdPartySnapshots)
        assertEquals(CapabilityStatus.Ready, caps.taskEnumeration)
        assertEquals(CapabilityStatus.Ready, caps.taskResume)
        assertEquals(CapabilityStatus.Unavailable, caps.taskRemoval)
        assertEquals(CapabilityStatus.Unavailable, caps.widgetHosting)
        assertEquals(CapabilityStatus.PermissionRequired, caps.deviceLock)
        assertEquals(CapabilityStatus.PermissionRequired, caps.postNotifications)
        org.junit.Assert.assertTrue(caps.hardwareKeyboard)
    }
}
