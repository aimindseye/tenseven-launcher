package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.live.AppUnreadCount

/**
 * The one choice of per-app unread counts that the Hub bar, the badges, the lock layer and the watch share:
 * the listener's waiting counts when it has any, otherwise the Hub's unread counts. Package and count only.
 */
object HubUnreadCounts {
    fun pick(
        lockCounts: Map<String, Int>,
        maxApps: Int,
        hubCounts: () -> List<AppUnreadCount>,
    ): List<AppUnreadCount> {
        val waiting =
            lockCounts.entries
                .filter { it.value > 0 }
                .map { AppUnreadCount(it.key, it.value) }
                .sortedWith(compareByDescending<AppUnreadCount> { it.count }.thenBy { it.packageName })
                .take(maxApps)
        return waiting.ifEmpty { hubCounts() }
    }
}
