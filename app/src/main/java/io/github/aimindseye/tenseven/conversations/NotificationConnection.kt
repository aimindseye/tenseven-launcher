package io.github.aimindseye.tenseven.conversations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-process listener status for diagnostics. Counts only: never log or persist notification text.
 * This is not a permission grant; access must still be enabled manually in Android Settings.
 */
object NotificationConnection {
    var connected: Boolean by mutableStateOf(false)
        private set
    var activeCount: Int by mutableIntStateOf(0)
        private set

    fun onConnected(active: Int) {
        connected = true
        activeCount = active.coerceAtLeast(0)
    }

    fun onSnapshot(active: Int) = onConnected(active)

    fun onDisconnected() {
        connected = false
        activeCount = 0
    }
}

/** Throttle repeated requestRebind after upgrades, process recreation or permission changes. */
object NotificationReconnectPolicy {
    const val MIN_RETRY_MS = 60_000L

    fun shouldRetry(
        declared: Boolean,
        granted: Boolean,
        connected: Boolean,
        lastAttemptMs: Long,
        nowMs: Long,
        permissionJustGranted: Boolean = false,
    ): Boolean =
        declared && granted && !connected &&
            (permissionJustGranted || lastAttemptMs < 0L || nowMs < lastAttemptMs || nowMs - lastAttemptMs >= MIN_RETRY_MS)
}
