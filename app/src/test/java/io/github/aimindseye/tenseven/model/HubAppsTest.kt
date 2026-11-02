package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAppsTest {
    @Test
    fun everyAppIsOffByDefault() {
        assertFalse(HubApps.isIncluded(emptySet(), "chat"))
        assertFalse(HubApps.isIncluded(emptySet(), "newly.installed.app"))
    }

    @Test
    fun toggleEnablesOnlyOneAppAndCanDisableAgain() {
        val enabled = HubApps.toggle(emptySet(), "chat")
        assertTrue(HubApps.isIncluded(enabled, "chat"))
        assertFalse(HubApps.isIncluded(enabled, "other"))
        assertEquals(emptySet<String>(), HubApps.toggle(enabled, "chat"))
    }

    @Test
    fun newAppRemainsOffAfterChoosingAnother() {
        val enabled = HubApps.toggle(emptySet(), "com.whatsapp")
        assertFalse(HubApps.isIncluded(enabled, "com.signal"))
    }

    @Test
    fun rowsListEachPackageOnceSortedByName() {
        val rows = HubApps.rows(listOf("b" to "Zed", "a" to "alpha", "b" to "Zed work"))
        assertEquals(listOf("a" to "alpha", "b" to "Zed"), rows)
    }
}
