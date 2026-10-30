// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7,
// features/conversations/NotificationHub.kt), Copyright 2026 Katoa Apps, Apache License 2.0.
// Changed: package; no Compose state (plain list plus change listeners); demo data and replies removed;
// unread counts for the Hub bar added.
package io.github.aimindseye.tenseven.conversations

import io.github.aimindseye.tenseven.live.AppUnreadCount
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The conversations currently in the notification shade, kept in memory only: nothing is written to disk,
 * and everything is dropped when notification access is revoked or the process ends.
 */
object ConversationHub {
    private val lock = Any()
    private var items: List<HubNotification> = emptyList()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun notifications(): List<HubNotification> = synchronized(lock) { items }

    fun conversations(): List<HubConversation> = buildConversations(notifications())

    /** Package and count per app, most unread first. No message text. */
    fun unreadCounts(maxApps: Int = DEFAULT_MAX_APPS): List<AppUnreadCount> = countUnread(notifications(), maxApps)

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    internal fun replace(newItems: List<HubNotification>) = update { newItems.sortedByDescending(HubNotification::postedAt) }

    internal fun upsert(item: HubNotification) = update { old -> (old.filterNot { it.key == item.key } + item).sortedByDescending(HubNotification::postedAt) }

    internal fun remove(key: String) = update { old -> old.filterNot { it.key == key } }

    internal fun clear() = update { emptyList() }

    private fun update(change: (List<HubNotification>) -> List<HubNotification>) {
        synchronized(lock) { items = change(items) }
        listeners.forEach { it() }
    }

    const val DEFAULT_MAX_APPS = 32
}

internal fun buildConversations(source: List<HubNotification>): List<HubConversation> =
    source
        .groupBy(HubNotification::conversationId)
        .map { (id, grouped) ->
            val ordered = grouped.sortedByDescending(HubNotification::postedAt)
            HubConversation(
                id = id,
                name = ordered.first().conversationName,
                notifications = ordered,
                messages = grouped.flatMap(HubNotification::messages).distinctBy(ConversationMessage::id).sortedBy(ConversationMessage::timestamp),
            )
        }
        .sortedByDescending { it.latestMessage?.timestamp ?: it.latestNotification.postedAt }

/** New incoming messages per app: ongoing notifications, outgoing messages and history do not count. */
internal fun countUnread(
    source: List<HubNotification>,
    maxApps: Int,
): List<AppUnreadCount> =
    source
        .filterNot(HubNotification::isOngoing)
        .groupBy(HubNotification::packageName)
        .mapValues { (_, items) -> items.sumOf { n -> n.messages.count { !it.isOutgoing && !it.isHistoric } } }
        .filterValues { it > 0 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maxApps)
        .map { AppUnreadCount(it.key, it.value) }
