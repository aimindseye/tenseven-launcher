package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {
    @Test
    fun idsAreUniqueAndEveryThemeHasItsWallpaper() {
        assertEquals(ThemePresets.ALL.size, ThemePresets.ALL.map { it.id }.toSet().size)
        assertEquals(WallpaperSets.ALL.size, WallpaperSets.ALL.map { it.id }.toSet().size)
        ThemePresets.ALL.forEach { theme ->
            assertEquals(theme.wallpaperId, WallpaperSets.byId(theme.wallpaperId).id)
        }
    }

    @Test
    fun noChoiceMeansDefault() {
        assertEquals(ThemePresets.DEFAULT_ID, ThemePresets.matching(null, null).id)
    }

    @Test
    fun storedModeAndAccentFindTheirPreset() {
        val forest = ThemePresets.byId("forest")
        assertEquals(forest, ThemePresets.matching(AppearanceMode.Dark.stableValue, AccentPreset.Green.stableValue))
    }

    @Test
    fun unknownCombinationFallsBackToDefault() {
        assertEquals(ThemePresets.DEFAULT_ID, ThemePresets.matching("light", "purple").id)
    }

    @Test
    fun wallpaperCountIsAtLeastEight() {
        assertTrue(WallpaperSets.ALL.size >= 8)
    }
}
