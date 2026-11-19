package io.github.aimindseye.tenseven.conversations

/** Metadata only, supplied after consent and successful notification conversion. */
internal data class NotificationGroupEntry(
    val key: String,
    val packageName: String,
    val groupKey: String?,
    val summary: Boolean,
)

/** A summary-only mail notification remains usable; child rows replace its duplicate summary. */
internal object NotificationGroupSelection {
    fun visibleKeys(entries: List<NotificationGroupEntry>): Set<String> {
        val childGroups = entries.filterNot { it.summary }.map { it.packageName to it.groupKey }.toSet()
        return entries
            .filter { !it.summary || it.groupKey == null || (it.packageName to it.groupKey) !in childGroups }
            .map { it.key }
            .toSet()
    }
}
