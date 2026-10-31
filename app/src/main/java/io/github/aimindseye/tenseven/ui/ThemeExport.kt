package io.github.aimindseye.tenseven.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import io.github.aimindseye.tenseven.model.CustomPalette
import io.github.aimindseye.tenseven.model.CustomTheme
import io.github.aimindseye.tenseven.model.CustomWallpaper
import io.github.aimindseye.tenseven.model.ThemePreset
import io.github.aimindseye.tenseven.model.WallpaperSets

/**
 * A built-in theme as a theme file, so it can be exported and used as a template. "Phone colours" follows the phone
 * and has no fixed colours, so it has no file (null).
 */
internal fun presetAsCustomTheme(preset: ThemePreset): CustomTheme? {
    if (preset.accent == AccentPreset.Phone || preset.mode == AppearanceMode.FollowSystem) return null
    val p = startPalette(dark = preset.mode == AppearanceMode.Dark, accent = preset.accent)
    fun rgb(c: Color) = c.toArgb() or 0xFF000000.toInt()
    val wallpaper = WallpaperSets.byId(preset.wallpaperId)
    return CustomTheme(
        id = "custom-" + preset.id,
        name = preset.title,
        mode = preset.mode.stableValue,
        accent = rgb(p.accent),
        onAccent = rgb(p.onAccent),
        palette =
            CustomPalette(
                background = rgb(p.background),
                card = rgb(p.card),
                tile = rgb(p.tile),
                line = rgb(p.line),
                text = rgb(p.text),
                muted = rgb(p.muted),
                dim = rgb(p.dim),
            ),
        wallpaper = CustomWallpaper(wallpaper.top, wallpaper.bottom, wallpaper.glow, wallpaper.ridge, wallpaper.seed % 10000),
    )
}
