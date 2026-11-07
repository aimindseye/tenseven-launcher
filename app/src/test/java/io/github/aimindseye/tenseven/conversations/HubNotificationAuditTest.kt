package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubNotificationAuditTest {
    @Test fun silenceIsNotAnExclusionReason() {
        assertEquals(
            HubAuditReason.VISIBLE,
            HubAuditPolicy.reason(true, false, true, HubAuditConversion(false, true, true)),
        )
        assertFalse(HubAuditReason.entries.any { it.name.contains("SILENT") })
    }

    @Test fun explainsSourceAndGroupOutcomesWithoutConflatingThem() {
        assertEquals(
            HubAuditReason.OTHER_PROFILE,
            HubAuditPolicy.reason(false, false, true, HubAuditConversion(false, true, true)),
        )
        assertEquals(
            HubAuditReason.USER_FILTER,
            HubAuditPolicy.reason(true, false, false, HubAuditConversion(false, true, true)),
        )
        assertEquals(
            HubAuditReason.PARSE_FAILURE,
            HubAuditPolicy.reason(true, false, true, HubAuditConversion(true, false, false)),
        )
        assertEquals(
            HubAuditReason.NO_HUB_CONTENT,
            HubAuditPolicy.reason(true, false, true, HubAuditConversion(false, false, false)),
        )
        assertEquals(
            HubAuditReason.GROUP_SUMMARY_REPLACED,
            HubAuditPolicy.reason(true, false, true, HubAuditConversion(false, true, false)),
        )
    }

    @Test fun exportIsAggregatedAndContainsNoIndividualKeys() {
        HubNotificationAudit.publish(
            listOf(
                HubAuditEvent("com.whatsapp", HubAuditReason.VISIBLE, HubAuditSound.SILENT_CANDIDATE),
                HubAuditEvent("com.whatsapp", HubAuditReason.NO_HUB_CONTENT, HubAuditSound.NOT_IDENTIFIED_SILENT),
                HubAuditEvent("com.mail", HubAuditReason.USER_FILTER, HubAuditSound.RANKING_UNKNOWN),
            ),
        )
        val report = HubNotificationAudit.report()
        assertTrue(report.contains("com.whatsapp | 2 |"))
        assertTrue(report.contains("com.mail | 1 |"))
        assertTrue(report.contains("Hub-visible: 1"))
        assertFalse(report.contains("notification_key_123"))
        HubNotificationAudit.clear()
        assertFalse(HubNotificationAudit.report().contains("com.whatsapp"))
    }
}
