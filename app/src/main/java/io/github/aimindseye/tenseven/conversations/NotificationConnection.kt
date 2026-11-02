package io.github.aimindseye.tenseven.conversations

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.platform.Capabilities

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

    fun onSnapshot(active: Int) {
        connected = true
        activeCount = active.coerceAtLeast(0)
    }

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
    ): Boolean =
        declared && granted && !connected &&
            (lastAttemptMs < 0L || nowMs < lastAttemptMs || nowMs - lastAttemptMs >= MIN_RETRY_MS)
}

object NotificationConnectionRecovery {
    private var lastRequestElapsedMs = -1L

    /** Request a reconnect only when permission exists and the listener is not currently connected. */
    @Synchronized
    fun onLauncherResume(context: Context) {
        val now = SystemClock.elapsedRealtime()
        val permitted = runCatching { ConversationAccess.isGranted(context) }.getOrDefault(false)
        if (NotificationReconnectPolicy.shouldRetry(
                declared = Capabilities.hasConversationService(context),
                granted = permitted,
                connected = NotificationConnection.connected,
                lastAttemptMs = lastRequestElapsedMs,
                nowMs = now,
            )) {
            lastRequestElapsedMs = now
            ConversationAccess.requestReconnect(context)
        }
    }

    /** Manually retry from Hub Settings. Does not bypass Android's restricted-settings flow. */
    @Synchronized
    fun retryFromSettings(context: Context) {
        if (!Capabilities.hasConversationService(context) ||
            !runCatching { ConversationAccess.isGranted(context) }.getOrDefault(false)) return
        lastRequestElapsedMs = SystemClock.elapsedRealtime()
        ConversationAccess.requestReconnect(context)
    }
}
