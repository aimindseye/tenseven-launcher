package io.github.aimindseye.tenseven.conversations

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStateTest {
    private fun message(
        conversation: String,
        text: String,
        at: Long,
        outgoing: Boolean = false,
        historic: Boolean = false,
    ) = ConversationMessage("m|$at|$text", conversation, "k", "p", "P", text, at, "Ann", outgoing, historic)

    private fun conversation(
        id: String,
        name: String,
        vararg messages: ConversationMessage,
        email: Boolean = false,
    ): HubConversation {
        val n =
            HubNotification(
                key = "k$id",
                conversationId = id,
                conversationName = name,
                packageName = "p",
                appName = "P",
                postedAt = messages.maxOf { it.timestamp },
                isOngoing = false,
                messages = messages.toList(),
                contentIntent = null,
                replyAction = null,
                isEmail = email,
            )
        return HubConversation(id, name, listOf(n), messages.toList())
    }

    @Test
    fun markingReadStopsCountingUntilNewMessagesArrive() {
        val c = conversation("contact:ann", "Ann", message("contact:ann", "hi", 100), message("contact:ann", "you there?", 200))
        assertEquals(2, ConversationState.EMPTY.unreadCount(c))
        val read = ConversationState.EMPTY.markRead(c.id, 200)
        assertEquals(0, read.unreadCount(c))
        val newer = conversation("contact:ann", "Ann", *(c.messages + message("contact:ann", "hello?", 300)).toTypedArray())
        assertEquals(1, read.unreadCount(newer))
    }

    @Test
    fun ownAndHistoricMessagesNeverCount() {
        val c = conversation("a", "Ann", message("a", "sent", 100, outgoing = true), message("a", "old", 90, historic = true))
        assertEquals(0, ConversationState.EMPTY.unreadCount(c))
    }

    @Test
    fun aSnoozeHidesUntilItsTimeThenWakesByItself() {
        val state = ConversationState.EMPTY.snooze("a", until = 1000)
        assertTrue(state.isSnoozed("a", now = 999))
        assertFalse(state.isSnoozed("a", now = 1000))
        assertFalse(state.isSnoozed("b", now = 0))
        assertFalse(state.wake("a").isSnoozed("a", 0))
        assertEquals(null, state.prune(now = 2000).snoozedUntilOf("a"))
    }

    @Test
    fun snoozedConversationsDoNotCountInTheHubBar() {
        val a = conversation("a", "Ann", message("a", "hi", 100))
        val b = conversation("b", "Bo", message("b", "yo", 100))
        val state = ConversationState.EMPTY.snooze("a", 5000)
        val counts = countUnread(a.notifications + b.notifications, 10, state, now = 1000)
        assertEquals(1, counts.sumOf { it.count })
        assertEquals(2, countUnread(a.notifications + b.notifications, 10).sumOf { it.count })
    }

    @Test
    fun storedTextHoldsOnlyHashesAndRoundTrips() {
        val state = ConversationState.EMPTY.markRead("contact:+15551234567", 5).snooze("a:direct:ann", 9)
        val text = state.encode()
        assertFalse(text.contains("15551234567"))
        assertFalse(text.contains("ann"))
        assertEquals(text, ConversationState.decode(text).encode())
        assertTrue(ConversationState.decode(text).isSnoozed("a:direct:ann", 0))
        assertEquals("", ConversationState.decode("junk;r=1;sXX=2;rzzzzzzzzzzzzzzzz=x").encode())
    }

    @Test
    fun filtersNarrowByPersonUnreadAndKind() {
        val ann = conversation("a", "Ann Lee", message("a", "hi", 100))
        val bo = conversation("b", "Bo", message("b", "invoice", 100), email = true)
        val all = listOf(ann, bo)
        val state = ConversationState.EMPTY.markRead("a", 100)
        fun ids(filter: ConversationFilter) = ConversationView.apply(all, filter, state, now = 0).map { it.id }
        assertEquals(listOf("a", "b"), ids(ConversationFilter()))
        assertEquals(listOf("a"), ids(ConversationFilter(person = "ann l")))
        assertEquals(listOf("b"), ids(ConversationFilter(unreadOnly = true)))
        assertEquals(listOf("b"), ids(ConversationFilter(kind = ConversationKind.Email)))
        assertEquals(listOf("a"), ids(ConversationFilter(kind = ConversationKind.Messages)))
        assertTrue(ConversationFilter(person = "x").active)
        assertFalse(ConversationFilter().active)
    }

    @Test
    fun snoozedConversationsAreListedOnlyByTheSnoozedFilter() {
        val ann = conversation("a", "Ann", message("a", "hi", 100))
        val state = ConversationState.EMPTY.snooze("a", 5000)
        assertEquals(emptyList<String>(), ConversationView.apply(listOf(ann), ConversationFilter(), state, 1000).map { it.id })
        assertEquals(listOf("a"), ConversationView.apply(listOf(ann), ConversationFilter(snoozed = true), state, 1000).map { it.id })
        assertEquals(1, ConversationView.snoozedCount(listOf(ann), state, 1000))
    }

    @Test
    fun tomorrowMorningIsTheNextEightAm() {
        val zone = ZoneId.of("America/New_York")
        fun at(h: Int) = ZonedDateTime.of(2026, 10, 30, h, 15, 0, 0, zone).toInstant().toEpochMilli()
        val eightNextDay = ZonedDateTime.of(2026, 10, 31, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val eightToday = ZonedDateTime.of(2026, 10, 30, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(eightNextDay, Snooze.nextMorning(at(12), zone))
        assertEquals(eightToday, Snooze.nextMorning(at(6), zone))
    }
}
