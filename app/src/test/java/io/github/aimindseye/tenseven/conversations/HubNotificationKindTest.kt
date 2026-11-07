package io.github.aimindseye.tenseven.conversations

import android.app.Notification
import io.github.aimindseye.tenseven.model.HubMessageKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HubNotificationKindTest {
    @Test fun ordinaryNoticesKeepIndependentNotificationIdentities() {
        assertEquals("app:notification:key1", conversationId("app", false, null, "key1", generic = true))
        assertEquals("app:notification:key2", conversationId("app", false, null, "key2", generic = true))
    }

    @Test fun categoriesAndStructuredMessagesRetainTheirMeaning() {
        assertEquals(HubMessageKind.Email, hubNotificationKind("mail", Notification.CATEGORY_EMAIL, false))
        assertEquals(HubMessageKind.Message, hubNotificationKind("chat", Notification.CATEGORY_MESSAGE, false))
        assertEquals(HubMessageKind.Message, hubNotificationKind("chat", null, true))
        assertEquals(
            HubMessageKind.Notification,
            hubNotificationKind("download", Notification.CATEGORY_PROGRESS, false),
        )
    }

    @Test fun ordinaryNoticesAreNotIncludedInTheMessagesFilter() {
        val notice =
            HubNotification(
                "n",
                "n",
                "Download",
                "p",
                "p",
                1L,
                false,
                emptyList(),
                null,
                null,
                kind = HubMessageKind.Notification,
            )
        val conversation = HubConversation("n", "Download", listOf(notice), emptyList())
        assertEquals(
            listOf(conversation),
            ConversationView.apply(listOf(conversation), ConversationFilter(), ConversationState.EMPTY, 1L),
        )
        assertEquals(
            emptyList<HubConversation>(),
            ConversationView.apply(
                listOf(conversation),
                ConversationFilter(kind = ConversationKind.Messages),
                ConversationState.EMPTY,
                1L,
            ),
        )
        assertEquals(
            "Notification options",
            io.github.aimindseye.tenseven.model.HubActionPolicy
                .panelTitle(notice.kind),
        )
    }
}
