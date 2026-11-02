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
        permissionJustGranted: Boolean = false,
    ): Boolean =
        declared && granted && !connected &&
            (permissionJustGranted || lastAttemptMs < 0L || nowMs < lastAttemptMs || nowMs - lastAttemptMs >= MIN_RETRY_MS)
}

object NotificationConnectionRecovery {
    private var lastRequestElapsedMs = -1L
    private var wasGranted = false
    private var resumeGeneration = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Request a reconnect only when permission exists and the listener is not currently connected. */
    @Synchronized
    fun onLauncherResume(context: Context) {
        val now = SystemClock.elapsedRealtime()
        val permitted = runCatching { ConversationAccess.isGranted(context) }.getOrDefault(false)
        val justGranted = permitted && !wasGranted
        wasGranted = permitted
        val generation = ++resumeGeneration
        val retryNeeded = NotificationReconnectPolicy.shouldRetry(
                declared = Capabilities.hasConversationService(context),
                granted = permitted,
                connected = NotificationConnection.connected,
                lastAttemptMs = lastRequestElapsedMs,
                nowMs = now,
                permissionJustGranted = justGranted,
            )
        if (retryNeeded) {
            lastRequestElapsedMs = now
            ConversationAccess.requestReconnect(context)
        }
        // OEM Settings can return before the grant/bind has settled. Recheck a bounded number
        // of times without retaining the Activity or asking the user to toggle access again.
        if (Capabilities.hasConversationService(context) && !NotificationConnection.connected && (retryNeeded || !permitted)) {
            val app = context.applicationContext
            for (delay in longArrayOf(1_000L, 3_000L, 10_000L)) {
                handler.postDelayed({
                    if (generation == resumeGeneration && !NotificationConnection.connected &&
                        runCatching { ConversationAccess.isGranted(app) }.getOrDefault(false)) {
                        wasGranted = true
                        retryFromSettings(app)
                    }
                }, delay)
            }
        }
    }

    /** Manually retry from Hub Settings. Does not bypass Android's restricted-settings flow. */
    @Synchronized
    fun retryFromSettings(context: Context) {
        if (!Capabilities.hasConversationService(context) ||
            !runCatching { ConversationAccess.isGranted(context) }.getOrDefault(false)) return
        if (ConversationListenerService.refreshConnected()) return
        lastRequestElapsedMs = SystemClock.elapsedRealtime()
        ConversationAccess.requestReconnect(context)
    }
}
