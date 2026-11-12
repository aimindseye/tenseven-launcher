package io.github.aimindseye.tenseven

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode

private val TENSEVEN_THEME_OUTLINE_VARIANT_COLOR = Color(0xFF39414A)

private val TENSEVEN_THEME_OUTLINE_COLOR = Color(0xFF59616A)

private val TENSEVEN_THEME_ON_SURFACE_VARIANT_COLOR = Color(0xFFA9B2BC)

private val TENSEVEN_THEME_SURFACE_VARIANT_COLOR = Color(0xFF0E1318)

private val TENSEVEN_THEME_SURFACE_COLOR = Color(0xFF070A0D)

private val TENSEVEN_THEME_PRIMARY_COLOR = Color(0xFF4D9CFF)

/**
 * Release 2 visual treatment for the launcher-owned utilities.
 * This wrapper is intentionally opt-in per app entry point. It does not recolor Home,
 * Active Frames, Android permission screens, or another app's window.
 * All saved wallpaper, accent, light/dark and external theme preferences remain effective.
 */
@Suppress("FunctionNaming")
@Composable
internal fun TenSeven2AppTheme(content: @Composable () -> Unit) {
    val appearance = rememberLauncherAppearance()
    val classic =
        appearance.mode == AppearanceMode.Dark &&
            appearance.accent == AccentPreset.Blue &&
            appearance.custom == null
    if (!classic) {
        content()
        return
    }
    val current = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme =
            current.copy(
                background = Color.Black,
                onBackground = Color.White,
                surface = TENSEVEN_THEME_SURFACE_COLOR,
                onSurface = Color.White,
                surfaceVariant = TENSEVEN_THEME_SURFACE_VARIANT_COLOR,
                onSurfaceVariant = TENSEVEN_THEME_ON_SURFACE_VARIANT_COLOR,
                outline = TENSEVEN_THEME_OUTLINE_COLOR,
                outlineVariant = TENSEVEN_THEME_OUTLINE_VARIANT_COLOR,
                primary = TENSEVEN_THEME_PRIMARY_COLOR,
                onPrimary = Color.Black,
            ),
        typography = MaterialTheme.typography,
        shapes =
            MaterialTheme.shapes.copy(
                extraSmall = RoundedCornerShape(2.dp),
                small = RoundedCornerShape(2.dp),
                medium = RoundedCornerShape(3.dp),
            ),
        content = content,
    )
}
