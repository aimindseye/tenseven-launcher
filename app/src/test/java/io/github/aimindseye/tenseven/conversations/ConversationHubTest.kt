package io.github.aimindseye.tenseven.conversations

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHubTest {
    private fun message(
        conversation: String,
        text: String,
        at: Long,
        pkg: String = "p.one",
        outgoing: Boolean = false,
        historic: Boolean = false,
        sender: String? = "Ann",
    ) = ConversationMessage(
        id = "$pkg|$at|$text",
        conversationId = conversation,
        notificationKey = "k",
        packageName = pkg,
        appName = pkg,
        text = text,
        timestamp = at,
        senderName = sender,
        isOutgoing = outgoing,
        isHistoric = historic,
    )

    private fun notification(
        key: String,
        conversation: String,
        pkg: String,
        at: Long,
        messages: List<ConversationMessage>,
        name: String = "Ann",
        ongoing: Boolean = false,
    ) = HubNotification(key, conversation, name, pkg, pkg, at, ongoing, messages, contentIntent = null, replyAction = null)

    @Test
    fun acceptsMessagesEmailAndStructuredMessages() {
        assertTrue(isConversationNotification(null, hasStructuredMessages = true))
        assertTrue(isConversationNotification(Notification.CATEGORY_MESSAGE, hasStructuredMessages = false))
        assertTrue(isConversationNotification(Notification.CATEGORY_EMAIL, hasStructuredMessages = false))
    }

    @Test
    fun rejectsOtherNotifications() {
        assertFalse(isConversationNotification(Notification.CATEGORY_PROMO, hasStructuredMessages = false))
        assertFalse(isConversationNotification(Notification.CATEGORY_REMINDER, hasStructuredMessages = false))
        assertFalse(isConversationNotification(null, hasStructuredMessages = false))
    }

    @Test
    fun groupTitleWinsForGroupsAndSenderForPeople() {
        assertEquals("Book club", conversationName(true, "Book club", "Ann", "Chat"))
        assertEquals("Ann", conversationName(false, "Re: hello", "Ann", "Chat"))
        assertEquals("Re: hello", conversationName(false, "Re: hello", null, "Chat"))
        assertEquals("Chat", conversationName(false, " ", null, "Chat"))
        assertEquals("Ann", conversationName(true, null, "Ann", "Chat"))
    }

    @Test
    fun samePersonInTwoAppsSharesAConversationButGroupsStayInTheirApp() {
        assertEquals("contact:abc", conversationId("a", false, "abc", "ann"))
        assertEquals("contact:abc", conversationId("b", false, "abc", "ann"))
        assertEquals("a:direct:ann", conversationId("a", false, null, "ann"))
        assertEquals("a:group:club", conversationId("a", true, "abc", "club"))
        assertEquals("annb", normalizeName("Ann-B!"))
    }

    @Test
    fun conversationsMergeAppsAndSortNewestFirst() {
        val a = notification("1", "contact:x", "p.one", 100, listOf(message("contact:x", "hi", 100)))
        val b = notification("2", "contact:x", "p.two", 300, listOf(message("contact:x", "hello", 300, "p.two")))
        val c = notification("3", "p.one:direct:bo", "p.one", 200, listOf(message("p.one:direct:bo", "yo", 200)), name = "Bo")
        val result = buildConversations(listOf(a, b, c))
        assertEquals(listOf("contact:x", "p.one:direct:bo"), result.map { it.id })
        assertEquals(listOf("hi", "hello"), result.first().messages.map { it.text })
        assertEquals(listOf("p.two", "p.one"), result.first().sourcePackages)
    }

    @Test
    fun unreadCountsSkipOngoingOutgoingAndHistory() {
        val items =
            listOf(
                notification("1", "c1", "p.one", 1, listOf(message("c1", "a", 1), message("c1", "b", 2), message("c1", "old", 0, historic = true))),
                notification("2", "c2", "p.two", 2, listOf(message("c2", "x", 2, "p.two"))),
                notification("3", "c3", "p.three", 3, listOf(message("c3", "call", 3, "p.three")), ongoing = true),
                notification("4", "c4", "p.four", 4, listOf(message("c4", "mine", 4, "p.four", outgoing = true))),
            )
        val counts = countUnread(items, maxApps = 10)
        assertEquals(listOf("p.one" to 2, "p.two" to 1), counts.map { it.packageName to it.count })
    }

    @Test
    fun unreadCountsAreCappedAndTiesBreakByPackage() {
        val items =
            listOf("p.c", "p.a", "p.b").mapIndexed { i, pkg ->
                notification("k$i", "c$i", pkg, i.toLong(), listOf(message("c$i", "t", i.toLong(), pkg)))
            }
        assertEquals(listOf("p.a", "p.b"), countUnread(items, maxApps = 2).map { it.packageName })
    }

    @Test
    fun hubIsReplacedUpdatedAndNotifiesListeners() {
        var changes = 0
        val listener: () -> Unit = { changes += 1 }
        ConversationHub.addChangeListener(listener)
        try {
            val first = notification("1", "c", "p.one", 1, listOf(message("c", "a", 1)))
            ConversationHub.replace(listOf(first))
            ConversationHub.upsert(first.copy(postedAt = 5))
            assertEquals(listOf(5L), ConversationHub.notifications().map { it.postedAt })
            assertEquals(1, ConversationHub.unreadCounts().single().count)
            ConversationHub.remove("1")
            assertTrue(ConversationHub.notifications().isEmpty())
            ConversationHub.clear()
            assertEquals(4, changes)
        } finally {
            ConversationHub.removeChangeListener(listener)
            ConversationHub.clear()
        }
    }
}
