package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReconnectPolicyTest {
    @Test fun grantedDisconnectedListenerCanRecoverImmediately() {
        assertTrue(NotificationReconnectPolicy.shouldRetry(
            declared = true, granted = true, connected = false,
            lastAttemptMs = -1L, nowMs = 10_000L,
        ))
    }

    @Test fun neverRequestsListenerWithoutPermissionOrDeclaredService() {
        assertFalse(NotificationReconnectPolicy.shouldRetry(false, true, false, -1L, 10_000L))
        assertFalse(NotificationReconnectPolicy.shouldRetry(true, false, false, -1L, 10_000L))
        assertFalse(NotificationReconnectPolicy.shouldRetry(true, true, true, -1L, 10_000L))
    }

    @Test fun aNewGrantBypassesAnEarlierRecoveryThrottle() {
        assertTrue(NotificationReconnectPolicy.shouldRetry(
            declared = true, granted = true, connected = false,
            lastAttemptMs = 10_000L, nowMs = 10_100L, permissionJustGranted = true,
        ))
        assertFalse(NotificationReconnectPolicy.shouldRetry(
            declared = true, granted = false, connected = false,
            lastAttemptMs = 10_000L, nowMs = 10_100L, permissionJustGranted = true,
        ))
    }

    @Test fun avoidsRepeatedBindingOnEveryHomeResume() {
        assertFalse(NotificationReconnectPolicy.shouldRetry(true, true, false, 10_000L, 10_100L))
        assertTrue(NotificationReconnectPolicy.shouldRetry(true, true, false, 10_000L, 70_000L))
        // Monotonic elapsed time resets after reboot.
        assertTrue(NotificationReconnectPolicy.shouldRetry(true, true, false, 70_000L, 1_000L))
    }
}
