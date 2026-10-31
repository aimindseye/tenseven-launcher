package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAppsTest {
    @Test
    fun everyAppIsInByDefault() {
        assertTrue(HubApps.isIncluded(emptySet(), "chat"))
    }

    @Test
    fun toggleTurnsAnAppOffAndBackOn() {
        val off = HubApps.toggle(emptySet(), "chat")
        assertFalse(HubApps.isIncluded(off, "chat"))
        assertEquals(emptySet<String>(), HubApps.toggle(off, "chat"))
    }

    @Test
    fun rowsListEachPackageOnceSortedByName() {
        val rows = HubApps.rows(listOf("b" to "Zed", "a" to "alpha", "b" to "Zed work"))
        assertEquals(listOf("a" to "alpha", "b" to "Zed"), rows)
    }
}
