package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.live.AppUnreadCount
import org.junit.Assert.assertEquals
import org.junit.Test

class HubSourceGroupsTest {
    @Test fun activeMessagesAndMailAreSummedWithoutDuplicatingAppChips() {
        val summary = HubSourceGroups.summarize(
            listOf(row("com.whatsapp", 2), row("org.thoughtcrime.securesms", 3),
                row("com.google.android.gm", 4), row("com.microsoft.office.outlook", 1),
                row("com.discord", 0), row("com.weather", 1), row("com.weather", 2)),
            setOf("com.whatsapp", "org.thoughtcrime.securesms",
                "com.google.android.gm", "com.microsoft.office.outlook", "com.discord", "com.weather"),
            emptySet(), emptySet(),
        )
        assertEquals(5, summary.messages)
        assertEquals(5, summary.email)
        assertEquals(listOf(HubSourceGroups.AppSource("com.weather", 3)), summary.otherApps)
    }

    @Test fun disabledAndZeroCountSourcesNeverAppearInBar() {
        val summary = HubSourceGroups.summarize(
            listOf(row("app.disabled", 10), row("app.enabled", 0), row("app.active", 2)),
            setOf("app.enabled", "app.active"), emptySet(), emptySet(),
        )
        assertEquals(0, summary.messages)
        assertEquals(0, summary.email)
        assertEquals(listOf(HubSourceGroups.AppSource("app.active", 2)), summary.otherApps)
    }

    @Test fun listenerClassifiesUnknownEmailAndMessageClients() {
        val summary = HubSourceGroups.summarize(
            listOf(row("com.private.mail", 3), row("com.private.chat", 4), row("other", 1)),
            setOf("com.private.mail", "com.private.chat", "other"),
            setOf("com.private.mail"), setOf("com.private.chat"),
        )
        assertEquals(3, summary.email)
        assertEquals(4, summary.messages)
        assertEquals(1, summary.otherApps.single().count)
    }

    @Test fun mailClassificationWinsForMixedClientsAndNeverDoubleCounts() {
        val summary = HubSourceGroups.summarize(
            listOf(row("com.microsoft.office.outlook", 2), row(HubBar.REDACTED, 9)),
            setOf("com.microsoft.office.outlook", HubBar.REDACTED),
            setOf("com.microsoft.office.outlook"), setOf("com.microsoft.office.outlook"),
        )
        assertEquals(2, summary.email)
        assertEquals(0, summary.messages)
        assertEquals(emptyList<HubSourceGroups.AppSource>(), summary.otherApps)
    }
}
