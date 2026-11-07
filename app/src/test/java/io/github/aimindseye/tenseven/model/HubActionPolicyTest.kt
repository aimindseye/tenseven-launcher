package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubActionPolicyTest {
    @Test fun emailTypeDoesNotInventReplyAllOrForward() {
        assertEquals("Email options", HubActionPolicy.panelTitle(true))
        assertEquals("Open email app", HubActionPolicy.openLabel(true))
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
