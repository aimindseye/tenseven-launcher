package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubVisibleTabsTest {
    @Test fun emptyNotificationStateShowsOnlyAll() {
        assertEquals(listOf("All"), HubVisibleTabs.items(0, 0, 0, 0, 0))
    }

    @Test fun onlyPopulatedSourcesAppear() {
        assertEquals(
            listOf("All", "Unread", "Messages", "Remind"),
            HubVisibleTabs.items(messages = 2, email = 0, unread = 4, snoozed = 0, reminders = 1),
        )
    }

    @Test fun silentNotificationsAreNotASeparateExclusion() {
        assertEquals(
            listOf("All", "Unread", "Email"),
            HubVisibleTabs.items(messages = 0, email = 3, unread = 3, snoozed = 0, reminders = 0),
        )
    }
}
