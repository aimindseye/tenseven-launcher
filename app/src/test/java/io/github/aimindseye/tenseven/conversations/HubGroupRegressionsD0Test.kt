package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Test

/** Records the Titan 2 WhatsApp observation without claiming the bug is fixed in D0. */
class HubGroupRegressionsD0Test {
    @Test fun twoUsableChatsAndOneSummaryKeepBothChats() {
        val input =
            listOf(
                NotificationGroupEntry("summary", "com.whatsapp", "whatsapp_group", true),
                NotificationGroupEntry("chatA", "com.whatsapp", "whatsapp_group", false),
                NotificationGroupEntry("chatB", "com.whatsapp", "whatsapp_group", false),
            )
        assertEquals(setOf("chatA", "chatB"), NotificationGroupSelection.visibleKeys(input))
    }

    @Test fun summaryRemainsWhenNoUsableChildWasConverted() {
        val input = listOf(NotificationGroupEntry("summary", "com.whatsapp", "whatsapp_group", true))
        assertEquals(setOf("summary"), NotificationGroupSelection.visibleKeys(input))
    }

    @Test fun unrelatedGroupsCannotMaskOneAnother() {
        val input =
            listOf(
                NotificationGroupEntry("sumA", "com.whatsapp", "groupA", true),
                NotificationGroupEntry("childB", "com.whatsapp", "groupB", false),
            )
        assertEquals(setOf("sumA", "childB"), NotificationGroupSelection.visibleKeys(input))
    }
}
