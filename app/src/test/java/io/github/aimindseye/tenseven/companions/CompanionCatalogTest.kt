package io.github.aimindseye.tenseven.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionCatalogTest {
    @Test
    fun `seven unique companion packages and ids`() {
        assertEquals(7, CompanionCatalog.all.size)
        val uniqueIds =
            CompanionCatalog.all
                .map { it.id }
                .toSet()
        val uniquePackages =
            CompanionCatalog.all
                .map { it.packageName }
                .toSet()
        assertEquals(7, uniqueIds.size)
        assertEquals(7, uniquePackages.size)
        assertFalse(CompanionCatalog.all.any { it.packageName == "io.github.aimindseye.tenseven" })
    }

    @Test
    fun `minimum and full are independent companion presets`() {
        assertEquals(setOf("tasks", "notes", "calculator"), CompanionCatalog.minimumPreset)
        assertEquals(7, CompanionCatalog.fullPreset.size)
        assertTrue(CompanionCatalog.fullPreset.containsAll(CompanionCatalog.minimumPreset))
        assertEquals(setOf("phone"), CompanionCatalog.normalize(listOf("phone", "unknown", "phone")))
    }

    @Test
    fun `ten games are one companion package`() {
        assertEquals(1, CompanionCatalog.all.count { it.id == "games" })
    }
}
