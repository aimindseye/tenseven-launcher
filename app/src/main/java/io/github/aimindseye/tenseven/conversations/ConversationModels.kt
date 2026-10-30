// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7,
// features/conversations/NotificationHub.kt), Copyright 2026 Katoa Apps, Apache License 2.0.
// Changed: package, demo data and reply bookkeeping removed, an unread flag added, pure naming helpers extracted.
package io.github.aimindseye.tenseven.conversations

import android.app.Notification
import android.app.PendingIntent
import java.util.Locale

/** One message of a conversation, as read from a notification. Held in memory only. */
data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val text: String,
    val timestamp: Long,
    val senderName: String?,
    val isOutgoing: Boolean,
    /** Part of the history Android attaches to a notification (already seen), not a new message. */
    val isHistoric: Boolean = false,
)

/** One active notification that is a message or an email. */
data class HubNotification(
    val key: String,
    val conversationId: String,
    val conversationName: String,
    val packageName: String,
    val appName: String,
    val postedAt: Long,
    val isOngoing: Boolean,
    val messages: List<ConversationMessage>,
    internal val contentIntent: PendingIntent?,
    internal val replyAction: Notification.Action?,
) {
    val canReply: Boolean get() = replyAction != null
}

/** Notifications of one person or group, across apps, newest first. */
data class HubConversation(
    val id: String,
    val name: String,
    val notifications: List<HubNotification>,
    val messages: List<ConversationMessage>,
) {
    val latestNotification: HubNotification get() = notifications.maxBy(HubNotification::postedAt)
    val openTarget: HubNotification get() = notifications.firstOrNull { it.contentIntent != null } ?: latestNotification
    val latestMessage: ConversationMessage? get() = messages.maxByOrNull(ConversationMessage::timestamp)
    val replyTarget: HubNotification?
        get() = notifications.filter(HubNotification::canReply).maxByOrNull(HubNotification::postedAt)
    val sourcePackages: List<String>
        get() = notifications.sortedByDescending(HubNotification::postedAt).map(HubNotification::packageName).distinct()
}

/** Messages and email count as conversations, as do notifications that carry structured messages. */
internal fun isConversationNotification(
    category: String?,
    hasStructuredMessages: Boolean,
): Boolean =
    hasStructuredMessages ||
        category == Notification.CATEGORY_MESSAGE ||
        category == Notification.CATEGORY_EMAIL

/** The name shown for a conversation: the group title, else the sender, else the title, else the app. */
internal fun conversationName(
    isGroup: Boolean,
    title: String?,
    sender: String?,
    appName: String,
): String =
    when {
        isGroup && !title.isNullOrBlank() -> title
        !sender.isNullOrBlank() -> sender
        !title.isNullOrBlank() -> title
        else -> appName
    }

/**
 * A stable id so the same person in two apps (same phone number or contact) lands in one conversation,
 * and a group or an unknown person stays within its app.
 */
internal fun conversationId(
    packageName: String,
    isGroup: Boolean,
    contactIdentity: String?,
    providerIdentity: String,
): String =
    when {
        isGroup -> "$packageName:group:$providerIdentity"
        contactIdentity != null -> "contact:$contactIdentity"
        else -> "$packageName:direct:$providerIdentity"
    }

internal fun normalizeName(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

/**
 * The line shown under a conversation's name: the latest message, with who said it in a group and "You" for
 * our own. null while the device is locked, so message text never shows on a locked screen.
 */
internal fun conversationPreview(
    conversation: HubConversation,
    locked: Boolean,
    you: String,
    maxChars: Int = 160,
): String? {
    if (locked) return null
    val latest = conversation.latestMessage ?: return ""
    val who =
        when {
            latest.isOutgoing -> you
            latest.senderName.isNullOrBlank() || latest.senderName == conversation.name -> null
            else -> latest.senderName
        }
    val text = latest.text.replace(Regex("\\s+"), " ").trim()
    val clipped = if (text.length > maxChars) text.take(maxChars - 1).trimEnd() + "…" else text
    return if (who == null) clipped else "$who: $clipped"
}
