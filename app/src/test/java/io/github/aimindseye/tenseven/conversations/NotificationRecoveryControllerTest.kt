package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.*
import org.junit.Test

class NotificationRecoveryControllerTest {
    private class Fixture {
        var time = 1000L
        var declared = true
        var granted = false
        var connected = false
        var rebinds = 0
        var refreshes = 0
        val delayed = mutableListOf<Pair<Long, () -> Unit>>()
        val controller =
            NotificationRecoveryController(
                now = { time },
                declared = { declared },
                granted = { granted },
                connected = { connected },
                rebind = { rebinds++ },
                refresh = {
                    refreshes++
                    connected
                },
                postDelayed = { delay, action -> delayed += delay to action },
            )
    }

    @Test fun aGrantThatSettlesAfterReturningFromSettingsRecoversWithoutAToggle() {
        val f = Fixture()
        f.controller.onResume()
        assertEquals(0, f.rebinds)
        assertEquals(listOf(1000L, 3000L, 10000L), f.delayed.map { it.first })
        f.granted = true
        f.delayed.first().second()
        assertEquals(1, f.rebinds)
        f.connected = true
        f.delayed.last().second()
        assertEquals(1, f.rebinds)
    }

    @Test fun aNewResumeInvalidatesOldCallbacksAndRepeatedResumesAreThrottled() {
        val f = Fixture().apply { granted = true }
        f.controller.onResume()
        assertEquals(1, f.rebinds)
        val stale = f.delayed.toList()
        f.controller.onResume()
        stale.forEach { it.second() }
        assertEquals(1, f.rebinds)
        f.time += NotificationReconnectPolicy.MIN_RETRY_MS
        f.controller.onResume()
        assertEquals(2, f.rebinds)
    }

    @Test fun permissionLossAndMissingServiceNeverRequestBindings() {
        val f = Fixture().apply { granted = true }
        f.controller.onResume()
        f.granted = false
        f.delayed.forEach { it.second() }
        f.controller.retryFromSettings()
        assertEquals(1, f.rebinds)
        val missing =
            Fixture().apply {
                declared = false
                granted = true
            }
        missing.controller.onResume()
        missing.controller.retryFromSettings()
        assertTrue(missing.delayed.isEmpty())
        assertEquals(0, missing.rebinds)
    }

    @Test fun retryRefreshesAConnectedListenerInsteadOfRebindingIt() {
        val f =
            Fixture().apply {
                granted = true
                connected = true
            }
        f.controller.onResume()
        assertTrue(f.delayed.isEmpty())
        f.controller.retryFromSettings()
        assertEquals(1, f.refreshes)
        assertEquals(0, f.rebinds)
        f.connected = false
        f.controller.retryFromSettings()
        assertEquals(1, f.rebinds)
    }

    @Test fun connectionDiagnosticsClampCountsAndClearOnDisconnect() {
        NotificationConnection.onConnected(-1)
        assertTrue(NotificationConnection.connected)
        assertEquals(0, NotificationConnection.activeCount)
        NotificationConnection.onSnapshot(4)
        assertEquals(4, NotificationConnection.activeCount)
        NotificationConnection.onDisconnected()
        assertFalse(NotificationConnection.connected)
        assertEquals(0, NotificationConnection.activeCount)
    }
}
