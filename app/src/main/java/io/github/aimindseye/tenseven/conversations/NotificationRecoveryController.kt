package io.github.aimindseye.tenseven.conversations

/** Permission/connection transitions and bounded retries, independent of Android callbacks. */
class NotificationRecoveryController(
    private val now: () -> Long,
    private val declared: () -> Boolean,
    private val granted: () -> Boolean,
    private val connected: () -> Boolean,
    private val rebind: () -> Unit,
    private val refresh: () -> Boolean,
    private val postDelayed: (Long, () -> Unit) -> Unit,
) {
    private var lastRequest = -1L
    private var wasGranted = false
    private var generation = 0

    fun onResume() {
        val permitted = granted()
        val justGranted = permitted && !wasGranted
        wasGranted = permitted
        val currentGeneration = ++generation
        val retry = NotificationReconnectPolicy.shouldRetry(
            declared(), permitted, connected(), lastRequest, now(), justGranted,
        )
        if (retry) requestRebind()
        if (declared() && !connected() && (retry || !permitted)) {
            for (delay in longArrayOf(1_000L, 3_000L, 10_000L)) {
                postDelayed(delay) {
                    if (currentGeneration == generation && !connected() && granted()) {
                        wasGranted = true
                        retryFromSettings()
                    }
                }
            }
        }
    }

    fun retryFromSettings() {
        if (!declared() || !granted()) return
        if (!refresh()) requestRebind()
    }

    private fun requestRebind() {
        lastRequest = now()
        rebind()
    }
}
