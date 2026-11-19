package io.github.aimindseye.tenseven.conversations

import io.github.aimindseye.tenseven.model.LockLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D2 protection for Titan 2 findings: grouped chats, silent sources and lock-preview privacy. */
class HubD2RegressionTest {
    @Test
    fun multipleWhatsAppChatsSurviveTheirGroupSummary() {
        val notifications =
            listOf(
                NotificationGroupEntry("summary", "com.whatsapp", "group", true),
                NotificationGroupEntry("first", "com.whatsapp", "group", false),
                NotificationGroupEntry("second", "com.whatsapp", "group", false),
            )
        assertEquals(setOf("first", "second"), NotificationGroupSelection.visibleKeys(notifications))
    }

    @Test
    fun summarySurvivesIfItsOnlyChildCouldNotBeConverted() {
        assertEquals(
            setOf("summary"),
            NotificationGroupSelection.visibleKeys(
                listOf(NotificationGroupEntry("summary", "com.whatsapp", "group", true)),
            ),
        )
    }

    @Test
    fun silentDeliveryIsNotAnExclusionReason() {
        val silent = HubAuditSound.SILENT_CANDIDATE
        val event =
            HubAuditEvent(
                "com.whatsapp",
                HubAuditPolicy.reason(
                    sameUser = true,
                    ownApp = false,
                    sourceEnabled = true,
                    conversion = HubAuditConversion(failed = false, converted = true, retained = true),
                ),
                silent,
            )
        assertEquals(HubAuditReason.VISIBLE, event.reason)
        assertEquals(HubAuditSound.SILENT_CANDIDATE, event.sound)
    }

    @Test
    fun lockCountsExcludeGroupSummariesAndOngoingButRetainSilentItems() {
        // LockLayer tally intentionally has no sound input or rejection rule.
        val notifications =
            listOf(
                Triple("com.whatsapp", false, true),
                Triple("com.whatsapp", false, false),
                Triple("com.whatsapp", false, false),
                Triple("com.music", true, false),
            )
        assertEquals(mapOf("com.whatsapp" to 2), LockLayer.tally(notifications, "io.github.aimindseye.tenseven"))
    }

    @Test
    fun selectedNotificationPreviewNeverShowsWhileLocked() {
        val msg =
            ConversationMessage(
                id = "m1",
                conversationId = "c1",
                notificationKey = "k1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                text = "A private test message",
                timestamp = 100L,
                senderName = "Sender",
                isOutgoing = false,
            )
        val source =
            HubNotification(
                key = "k1",
                conversationId = "c1",
                conversationName = "Sender",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                postedAt = 100L,
                isOngoing = false,
                messages = listOf(msg),
                contentIntent = null,
                replyAction = null,
            )
        val conversation = HubConversation("c1", "Sender", listOf(source), listOf(msg))
        assertNull(conversationPreview(conversation, locked = true, you = "You"))
        val readable = conversationPreview(conversation, locked = false, you = "You")
        assertTrue(readable.orEmpty().contains("A private test message"))
        assertFalse(readable.orEmpty().contains("notificationKey"))
    }
}
