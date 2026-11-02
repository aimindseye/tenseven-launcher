package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode

/** A named look for the launcher: light or dark, an accent colour, and the wallpaper set drawn to go with it. */
data class ThemePreset(
    val id: String,
    val title: String,
    val mode: AppearanceMode,
    val accent: AccentPreset,
    val wallpaperId: String,
)

/**
 * The themes on offer in Launcher Settings. "BlackBerry" is the default: it stores no choice of its own, and is pure
 * black with the BlackBerry blue. "Phone colours" follows the phone's wallpaper colours and light or dark setting;
 * the rest set the mode and accent for the launcher's own screens and keep working on an ordinary install.
 */
object ThemePresets {
    const val DEFAULT_ID = "blackberry"
    const val PHONE_ID = "phone"

    val ALL: List<ThemePreset> =
        listOf(
            ThemePreset(DEFAULT_ID, "BlackBerry", AppearanceMode.Dark, AccentPreset.Blue, "sky"),
            ThemePreset(PHONE_ID, "Phone colours", AppearanceMode.FollowSystem, AccentPreset.Phone, "slate"),
            ThemePreset("midnight", "Midnight", AppearanceMode.Dark, AccentPreset.Purple, "midnight"),
            ThemePreset("daylight", "Daylight", AppearanceMode.Light, AccentPreset.Blue, "cream"),
            ThemePreset("forest", "Forest", AppearanceMode.Dark, AccentPreset.Green, "forest"),
            ThemePreset("dusk", "Dusk", AppearanceMode.Dark, AccentPreset.Orange, "dusk"),
            ThemePreset("amber", "Amber", AppearanceMode.Light, AccentPreset.Orange, "ember"),
            ThemePreset("slate", "Slate", AppearanceMode.Dark, AccentPreset.Slate, "slate"),
        )

    fun byId(id: String?): ThemePreset = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** The preset whose stored mode and accent these are; null values mean no choice made, which is the default. */
    fun matching(
        mode: String?,
        accent: String?,
    ): ThemePreset {
        if (mode == null && accent == null) return ALL.first()
        val chosenMode = AppearanceMode.fromStableValue(mode)
        val chosenAccent = AccentPreset.fromStableValue(accent)
        return ALL.drop(1).firstOrNull { it.mode == chosenMode && it.accent == chosenAccent } ?: ALL.first()
    }
}

/**
 * One original wallpaper, drawn in code from a few colours (no image files, no borrowed artwork): a vertical
 * gradient from [top] to [bottom], a soft [glow] disc, and layered ridges in [ridge] whose shape comes from [seed].
 */
data class WallpaperSet(
    val id: String,
    val title: String,
    val top: Int,
    val bottom: Int,
    val glow: Int,
    val ridge: Int,
    val seed: Int,
)

object WallpaperSets {
    val ALL: List<WallpaperSet> =
        listOf(
            WallpaperSet("sky", "Sky", 0xFF1F6FEB.toInt(), 0xFF9CD3FF.toInt(), 0xFFFFF4C2.toInt(), 0xFF0B3C8C.toInt(), 11),
            WallpaperSet("midnight", "Midnight", 0xFF0B0B1F.toInt(), 0xFF3B2A7A.toInt(), 0xFFB9A7FF.toInt(), 0xFF05050F.toInt(), 23),
            WallpaperSet("cream", "Cream", 0xFFFFF3DC.toInt(), 0xFFF2C9A0.toInt(), 0xFFFFFFFF.toInt(), 0xFFB9855A.toInt(), 37),
            WallpaperSet("forest", "Forest", 0xFF06231A.toInt(), 0xFF2E7D5B.toInt(), 0xFFD4FFC8.toInt(), 0xFF03130E.toInt(), 41),
            WallpaperSet("dusk", "Dusk", 0xFF2B1236.toInt(), 0xFFF2784B.toInt(), 0xFFFFD9A0.toInt(), 0xFF150A1C.toInt(), 53),
            WallpaperSet("ember", "Ember", 0xFF3A0D05.toInt(), 0xFFFF9A3C.toInt(), 0xFFFFE3A8.toInt(), 0xFF1D0602.toInt(), 67),
            WallpaperSet("slate", "Slate", 0xFF141A22.toInt(), 0xFF5B6B7F.toInt(), 0xFFD7E0EA.toInt(), 0xFF0A0D12.toInt(), 71),
            WallpaperSet("aurora", "Aurora", 0xFF04121F.toInt(), 0xFF1BB59A.toInt(), 0xFF9BFFE0.toInt(), 0xFF020A12.toInt(), 83),
        )

    fun byId(id: String?): WallpaperSet = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** A saved explicit choice wins; otherwise use the theme's wallpaper, including for old installations. */
    fun selectedId(savedId: String?, themeDefaultId: String): String =
        ALL.firstOrNull { it.id == savedId }?.id ?: byId(themeDefaultId).id
}
