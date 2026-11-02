package io.github.aimindseye.tenseven.conversations

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailNotificationPolicyTest {
    private val yahoo = "com.yahoo.mobile.client.android.mail"

    @Test fun yahooWithoutCategoryIsIncludedAsEmailButUnrelatedUncategorizedAppsAreNot() {
        assertTrue(isConversationNotification(null, false, yahoo))
        assertTrue(isEmailNotification(yahoo, null))
        assertTrue(isEmailNotification(yahoo, Notification.CATEGORY_MESSAGE))
        assertFalse(isConversationNotification(null, false, "com.example.weather"))
        assertFalse(isEmailNotification("com.example.weather", null))
    }

    @Test fun explicitNonMailCategoriesAreNotPromotedJustBecauseTheAppIsAMailClient() {
        for (category in listOf(Notification.CATEGORY_PROMO, Notification.CATEGORY_SERVICE, Notification.CATEGORY_STATUS)) {
            assertFalse(isConversationNotification(category, false, yahoo))
            assertFalse(isEmailNotification(yahoo, category))
        }
        assertTrue(isEmailNotification("com.private.mail", Notification.CATEGORY_EMAIL))
    }

    @Test fun inboxStyleLinesAreUsedWhenStandardTextExtrasAreMissingOrBlank() {
        assertEquals("Sender one: Subject one\nSender two: Subject two",
            fallbackNotificationText(" ", null,
                listOf("Sender one: Subject one", "", "Sender two: Subject two"), "2 new emails", "Yahoo Mail"))
        assertEquals("Full email preview", fallbackNotificationText("Full email preview", "Short preview",
            listOf("Inbox line"), "Summary", "Title"))
        assertEquals("Short preview", fallbackNotificationText(null, "Short preview", emptyList(), null, "Title"))
        assertEquals("New mail", fallbackNotificationText(null, null, emptyList(), "New mail", "Title"))
        assertEquals("Title", fallbackNotificationText(null, null, emptyList(), null, "Title"))
        assertEquals(null, fallbackNotificationText(null, " ", emptyList(), "", null))
    }
}
