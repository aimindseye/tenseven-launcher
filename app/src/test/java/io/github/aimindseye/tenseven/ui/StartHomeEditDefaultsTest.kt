package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.QuickBar
import org.junit.Assert.assertEquals
import org.junit.Test

class StartHomeEditDefaultsTest {
    @Test
    fun keepsInstalledPreferredAppsThenRemainingAppsWithOriginalDefaults() {
        val result = startHomeEditDefaults(listOf("gone", "b", "b"), listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), result.gridOrder)
        assertEquals(QuickBar.DEFAULT, result.quickBar)
        assertEquals(GlanceWidgets.DEFAULT, result.glanceWidgets)
        assertEquals(HomeLayout.DEFAULT_HIDDEN, result.hiddenSections)
    }

    @Test
    fun allowsEmptyInstalledCatalogWithoutInventingGridEntries() {
        assertEquals(emptyList<String>(), startHomeEditDefaults(listOf("gone"), emptyList()).gridOrder)
    }
}
