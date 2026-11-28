package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubVisibleTabsTest {
    @Test fun tabsAreTheApprovedBoardSet() {
        assertEquals(listOf("All", "Messages", "Calls", "Contacts"), HubVisibleTabs.items())
    }
}
