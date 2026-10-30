package io.github.aimindseye.tenseven.conversations

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/**
 * What the user has done to conversations in the Hub: read up to a time, or snoozed until a time. Conversation ids
 * can hold a contact key or a phone number, so only a short hash of each id is kept (here and on disk), never the
 * id or any message text. Pure policy, unit tested.
 */
class ConversationState private constructor(
    private val readUpTo: Map<String, Long>,
    private val snoozedUntil: Map<String, Long>,
) {
    fun markRead(
        conversationId: String,
        at: Long,
    ): ConversationState = ConversationState(readUpTo + (hash(conversationId) to at), snoozedUntil)

    fun snooze(
        conversationId: String,
        until: Long,
    ): ConversationState = ConversationState(readUpTo, snoozedUntil + (hash(conversationId) to until))

    fun wake(conversationId: String): ConversationState = ConversationState(readUpTo, snoozedUntil - hash(conversationId))

    fun isSnoozed(
        conversationId: String,
        now: Long,
    ): Boolean = (snoozedUntil[hash(conversationId)] ?: 0L) > now

    fun snoozedUntilOf(conversationId: String): Long? = snoozedUntil[hash(conversationId)]

    /** Incoming, new messages after the time the conversation was marked read. */
    fun unreadCount(conversation: HubConversation): Int {
        val after = readUpTo[hash(conversation.id)] ?: 0L
        return conversation.messages.count { !it.isOutgoing && !it.isHistoric && it.timestamp > after }
    }

    fun unreadCount(
        notification: HubNotification,
    ): Int {
        val after = readUpTo[hash(notification.conversationId)] ?: 0L
        return notification.messages.count { !it.isOutgoing && !it.isHistoric && it.timestamp > after }
    }

    /** Drops entries that no longer matter: snoozes that ended. Read marks are kept (a hash and a time). */
    fun prune(now: Long): ConversationState = ConversationState(readUpTo, snoozedUntil.filterValues { it > now })

    fun encode(): String =
        (readUpTo.entries.map { "r${it.key}=${it.value}" } + snoozedUntil.entries.map { "s${it.key}=${it.value}" })
            .sorted()
            .joinToString(";")

    companion object {
        val EMPTY = ConversationState(emptyMap(), emptyMap())
        private const val MAX_ENTRIES = 300

        fun hash(conversationId: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(conversationId.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }

        /** Reads what [encode] wrote; a damaged entry is dropped. */
        fun decode(text: String?): ConversationState {
            val read = linkedMapOf<String, Long>()
            val snoozed = linkedMapOf<String, Long>()
            text.orEmpty().split(';').forEach { part ->
                val key = part.substringBefore('=').drop(1)
                val value = part.substringAfter('=', "").toLongOrNull()
                if (key.length != 16 || value == null) return@forEach
                when (part.firstOrNull()) {
                    'r' -> read[key] = value
                    's' -> snoozed[key] = value
                }
            }
            return ConversationState(read.entries.take(MAX_ENTRIES).associate { it.toPair() }, snoozed.entries.take(MAX_ENTRIES).associate { it.toPair() })
        }
    }
}

/** How long a snooze lasts. */
object Snooze {
    const val ONE_HOUR_MS = 60L * 60 * 1000

    /** The next 8:00 in [zone] after [now] (tomorrow morning; today's 8:00 if it is still ahead before 8:00). */
    fun nextMorning(
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        hour: Int = 8,
    ): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val today = local.toLocalDate().atTime(hour, 0).atZone(zone)
        val target = if (today.toInstant().toEpochMilli() > now) today else today.plusDays(1)
        return target.toInstant().toEpochMilli()
    }
}

enum class ConversationKind { All, Messages, Email }

/** The Hub list's filters: part of a person's name, unread only, and messages versus email. */
data class ConversationFilter(
    val person: String = "",
    val unreadOnly: Boolean = false,
    val kind: ConversationKind = ConversationKind.All,
    val snoozed: Boolean = false,
) {
    val active: Boolean get() = person.isNotBlank() || unreadOnly || kind != ConversationKind.All || snoozed
}

object ConversationView {
    /**
     * [conversations] as the Hub shows them. Snoozed conversations are hidden (the filter's [ConversationFilter.snoozed]
     * lists only those), and a conversation shows again by itself once its snooze has ended.
     */
    fun apply(
        conversations: List<HubConversation>,
        filter: ConversationFilter,
        state: ConversationState,
        now: Long,
    ): List<HubConversation> {
        val person = normalizeName(filter.person)
        return conversations.filter { c ->
            state.isSnoozed(c.id, now) == filter.snoozed &&
                (person.isEmpty() || normalizeName(c.name).contains(person)) &&
                (!filter.unreadOnly || state.unreadCount(c) > 0) &&
                when (filter.kind) {
                    ConversationKind.All -> true
                    ConversationKind.Email -> c.isEmail
                    ConversationKind.Messages -> !c.isEmail
                }
        }
    }

    fun snoozedCount(
        conversations: List<HubConversation>,
        state: ConversationState,
        now: Long,
    ): Int = conversations.count { state.isSnoozed(it.id, now) }
}
