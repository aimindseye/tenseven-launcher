package io.github.aimindseye.tenseven.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionCatalogTest {
    @Test
    fun sevenUniqueCompanionPackagesAndIds() {
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
    fun minimumAndFullAreIndependentCompanionPresets() {
        assertEquals(setOf("tasks", "notes", "calculator"), CompanionCatalog.minimumPreset)
        assertEquals(7, CompanionCatalog.fullPreset.size)
        assertTrue(CompanionCatalog.fullPreset.containsAll(CompanionCatalog.minimumPreset))
        assertEquals(setOf("phone"), CompanionCatalog.normalize(listOf("phone", "unknown", "phone")))
    }

    @Test
    fun tenGamesAreOneCompanionPackage() {
        assertEquals(1, CompanionCatalog.all.count { it.id == "games" })
    }
}
