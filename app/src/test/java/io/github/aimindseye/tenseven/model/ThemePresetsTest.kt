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
    fun phoneColoursIsAThemeOfItsOwn() {
        val phone = ThemePresets.byId(ThemePresets.PHONE_ID)
        assertEquals(AccentPreset.Phone, phone.accent)
        assertEquals(phone, ThemePresets.matching(AppearanceMode.FollowSystem.stableValue, AccentPreset.Phone.stableValue))
    }

    @Test
    fun defaultIsBlackBerryBlackWithBlue() {
        val default = ThemePresets.byId(ThemePresets.DEFAULT_ID)
        assertEquals("BlackBerry", default.title)
        assertEquals(AppearanceMode.Dark, default.mode)
        assertEquals(AccentPreset.Blue, default.accent)
    }

    @Test
    fun wallpaperCountIsAtLeastEight() {
        assertTrue(WallpaperSets.ALL.size >= 8)
    }
}
