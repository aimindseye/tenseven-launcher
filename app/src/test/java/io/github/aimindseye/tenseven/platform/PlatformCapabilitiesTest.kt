package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformCapabilitiesTest {
    @Test fun ordinaryInstallNeverPromisesForeignAppSnapshots() {
        val caps = PlatformCapabilities.evaluate(
            hasListenerService = true, listenerAllowed = false, usageAllowed = true,
            calendarAllowed = false, overlayAllowed = false, trustedSnapshotBridge = false,
        )
        assertEquals(CapabilityStatus.PermissionRequired, caps.hubNotifications)
        assertEquals(CapabilityStatus.Ready, caps.usageRecents)
        assertEquals(CapabilityStatus.Unavailable, caps.thirdPartySnapshots)
        assertEquals(CapabilityStatus.Ready, caps.localTasks)
    }

    @Test fun missingServiceIsNotMistakenForPermissionRequired() {
        val caps = PlatformCapabilities.evaluate(
            hasListenerService = false, listenerAllowed = true, usageAllowed = false,
            calendarAllowed = true, overlayAllowed = true, trustedSnapshotBridge = false,
        )
        assertEquals(CapabilityStatus.Unavailable, caps.hubNotifications)
        assertEquals(CapabilityStatus.Ready, caps.calendarEvents)
        assertEquals(CapabilityStatus.Ready, caps.localTasks)
    }
    @Test fun taskAndDeviceCapabilitiesAreIndependentAndDetected() {
        val caps = PlatformCapabilities.evaluate(true, true, true, true, true, true,
            FramePlatformAccess(tasks = true, resume = true, removal = false, keyboard = true,
                lock = CapabilityStatus.PermissionRequired, postNotifications = false, widgets = false))
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
