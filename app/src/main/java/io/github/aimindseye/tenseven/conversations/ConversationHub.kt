// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7,
// features/conversations/NotificationHub.kt), Copyright 2026 Katoa Apps, Apache License 2.0.
// Changed: package; no Compose state (plain list plus change listeners); demo data removed; replies kept in
// memory and sent through the notification's own reply action; unread counts for the Hub bar added.
package io.github.aimindseye.tenseven.conversations

import android.app.Activity
import android.app.ActivityOptions
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import io.github.aimindseye.tenseven.live.AppUnreadCount
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * The conversations currently in the notification shade, kept in memory only: nothing is written to disk,
 * and everything is dropped when notification access is revoked or the process ends.
 */
object ConversationHub {
    private val lock = Any()
    private var items: List<HubNotification> = emptyList()
    private var sentReplies: List<ConversationMessage> = emptyList()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun notifications(): List<HubNotification> = synchronized(lock) { items }

    fun conversations(): List<HubConversation> = synchronized(lock) { buildConversations(items, sentReplies) }

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
        synchronized(lock) {
            items = change(items)
            // A reply belongs to a conversation that is still in the shade; drop the rest.
            val active = items.map(HubNotification::conversationId).toSet()
            sentReplies = sentReplies.filter { it.conversationId in active }
        }
        listeners.forEach { it() }
    }

    /** Opens the app at the conversation, through the notification's own intent. False when it has none. */
    fun open(
        context: Context,
        item: HubNotification,
    ): Boolean {
        val pendingIntent = item.contentIntent ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options =
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                pendingIntent.send(context, 0, null, null, null, null, options)
            } else if (context is Activity) {
                context.startIntentSender(pendingIntent.intentSender, null, 0, 0, 0)
            } else {
                pendingIntent.send()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Sends [text] through the conversation's reply action, the same one the notification's own Reply button
     * uses, and shows it in the conversation. False when there is no reply action or the app refused.
     */
    fun reply(
        context: Context,
        conversation: HubConversation,
        text: String,
    ): Boolean {
        val clean = text.trim()
        val target = conversation.replyTarget
        val action = target?.replyAction
        val inputs: Array<RemoteInput>? = action?.remoteInputs?.filter(RemoteInput::getAllowFreeFormInput)?.toTypedArray()
        if (clean.isEmpty() || target == null || action == null || inputs.isNullOrEmpty()) return false
        val sent =
            runCatching {
                val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, clean) } }
                val fillIn = Intent()
                RemoteInput.addResultsToIntent(inputs, fillIn, results)
                action.actionIntent.send(context, 0, fillIn)
                true
            }.getOrDefault(false)
        if (sent) {
            val now = System.currentTimeMillis()
            val message =
                ConversationMessage(
                    id = "local|${conversation.id}|$now|${System.nanoTime()}",
                    conversationId = conversation.id,
                    notificationKey = target.key,
                    packageName = target.packageName,
                    appName = target.appName,
                    text = clean,
                    timestamp = now,
                    senderName = null,
                    isOutgoing = true,
                )
            synchronized(lock) { sentReplies = sentReplies + message }
            listeners.forEach { it() }
        }
        return sent
    }

    const val DEFAULT_MAX_APPS = 32
}

/** Groups notifications into conversations; [replies] are the user's own, kept until the app echoes them. */
internal fun buildConversations(
    source: List<HubNotification>,
    replies: List<ConversationMessage> = emptyList(),
): List<HubConversation> =
    source
        .groupBy(HubNotification::conversationId)
        .map { (id, grouped) ->
            val ordered = grouped.sortedByDescending(HubNotification::postedAt)
            val fromApps = grouped.flatMap(HubNotification::messages)
            val local = replies.filter { it.conversationId == id && !isEchoed(it, fromApps) }
            HubConversation(
                id = id,
                name = ordered.first().conversationName,
                notifications = ordered,
                messages = (fromApps + local).distinctBy(ConversationMessage::id).sortedBy(ConversationMessage::timestamp),
            )
        }.sortedByDescending { it.latestMessage?.timestamp ?: it.latestNotification.postedAt }

/** Once the app lists our reply among its own messages, the local copy would show it twice. */
private fun isEchoed(
    reply: ConversationMessage,
    fromApps: List<ConversationMessage>,
): Boolean =
    fromApps.any { it.isOutgoing && it.text == reply.text && abs(it.timestamp - reply.timestamp) < ECHO_WINDOW_MS }

private const val ECHO_WINDOW_MS = 120_000L

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
