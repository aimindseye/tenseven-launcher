package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubActionPolicyTest {
    @Test fun messageAndOrdinaryNotificationLabelsStayDistinct() {
        assertEquals("Message options", HubActionPolicy.panelTitle(HubMessageKind.Message))
        assertEquals("Open messaging app", HubActionPolicy.openLabel(HubMessageKind.Message))
        assertEquals("Notification options", HubActionPolicy.panelTitle(HubMessageKind.Notification))
        assertEquals("Open app", HubActionPolicy.openLabel(HubMessageKind.Notification))
    }

    @Test fun emailTypeDoesNotInventReplyAllOrForward() {
        assertEquals("Email options", HubActionPolicy.panelTitle(HubMessageKind.Email))
        assertEquals("Open email app", HubActionPolicy.openLabel(HubMessageKind.Email))
        assertEquals(emptyList<Int>(), HubActionPolicy.visibleIndices(emptyList(), false))
    }

    @Test fun providerReplyAllAndForwardAreOfferedWithoutGuessingTheirTranslatedLabels() {
        val actions =
            listOf(
                HubActionCapability("Répondre à tous", true, HubActionInput.Text),
                HubActionCapability("Forward", true, HubActionInput.None),
            )
        assertEquals(listOf(0, 1), HubActionPolicy.visibleIndices(actions, false))
        assertEquals(emptyList<Int>(), HubActionPolicy.visibleIndices(actions, true))
    }

    @Test fun unavailableUnsupportedAndCommonActionsDoNotBecomeExtraMenuItems() {
        val actions =
            listOf(
                HubActionCapability("Reply", true, HubActionInput.Text, alreadyHandled = true),
                HubActionCapability("Mark read", true, HubActionInput.None, alreadyHandled = true),
                HubActionCapability("Attachment", true, HubActionInput.Unsupported),
                HubActionCapability("", true, HubActionInput.None),
                HubActionCapability("No target", false, HubActionInput.None),
                HubActionCapability("Archive", true, HubActionInput.None),
            )
        assertEquals(listOf(5), HubActionPolicy.visibleIndices(actions, false))
    }
}
