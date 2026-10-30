package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Test

class MailSummaryTest {
    @Test
    fun noMailSaysSo() {
        assertEquals("No new email", MailSummary.headline(0, null, null, locked = false))
    }

    @Test
    fun theNewestSenderAndTextShowWhenUnlocked() {
        assertEquals("3 new · Ann: Lunch tomorrow?", MailSummary.headline(3, "Ann", "Lunch\n  tomorrow?", locked = false))
        assertEquals("1 new · Ann", MailSummary.headline(1, "Ann", " ", locked = false))
    }

    @Test
    fun aLockedPhoneShowsOnlyACount() {
        assertEquals("2 new emails", MailSummary.headline(2, "Ann", "secret", locked = true))
        assertEquals("1 new email", MailSummary.headline(1, "Ann", "secret", locked = true))
        assertEquals("2 new emails", MailSummary.headline(2, null, null, locked = false))
    }

    @Test
    fun longTextIsClipped() {
        val line = MailSummary.headline(1, "Ann", "x".repeat(200), locked = false)
        assertEquals(true, line.length < 90 && line.endsWith("…"))
    }

    @Test
    fun messagesFollowTheSameRules() {
        assertEquals("No new messages", MailSummary.messagesHeadline(0, null, null, locked = false))
        assertEquals("2 new · Bo: running late", MailSummary.messagesHeadline(2, "Bo", "running late", locked = false))
        assertEquals("1 new message", MailSummary.messagesHeadline(1, "Bo", "secret", locked = true))
        assertEquals("3 new messages", MailSummary.messagesHeadline(3, null, null, locked = false))
    }
}
