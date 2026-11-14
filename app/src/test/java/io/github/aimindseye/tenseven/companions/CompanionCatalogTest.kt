package io.github.aimindseye.tenseven.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionCatalogTest {
    @Test
    fun sixUniqueToolCompanionPackagesAndIds() {
        assertEquals(6, CompanionCatalog.all.size)
        val uniqueIds =
            CompanionCatalog.all
                .map { it.id }
                .toSet()
        val uniquePackages =
            CompanionCatalog.all
                .map { it.packageName }
                .toSet()
        assertEquals(6, uniqueIds.size)
        assertEquals(6, uniquePackages.size)
        assertFalse(CompanionCatalog.all.any { it.packageName == "io.github.aimindseye.tenseven" })
    }

    @Test
    fun minimumAndFullAreIndependentCompanionPresets() {
        assertEquals(setOf("tasks", "notes", "calculator"), CompanionCatalog.minimumPreset)
        assertEquals(6, CompanionCatalog.fullPreset.size)
        assertTrue(CompanionCatalog.fullPreset.containsAll(CompanionCatalog.minimumPreset))
        assertEquals(setOf("phone"), CompanionCatalog.normalize(listOf("phone", "unknown", "phone")))
    }

    @Test
    fun obsoleteGamesPackIsNotACompanion() {
        assertFalse(CompanionCatalog.all.any { it.id == "games" })
        assertEquals(emptySet<String>(), CompanionCatalog.normalize(listOf("games")))
    }
}
