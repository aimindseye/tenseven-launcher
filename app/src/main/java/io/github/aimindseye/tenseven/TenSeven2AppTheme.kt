package io.github.aimindseye.tenseven

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode

/**
 * Release 2 visual treatment for the launcher-owned utilities.
 * This wrapper is intentionally opt-in per app entry point. It does not recolor Home,
 * Active Frames, Android permission screens, or another app's window.
 * All saved wallpaper, accent, light/dark and external theme preferences remain effective.
 */
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
                surface = Color(0xFF070A0D),
                onSurface = Color.White,
                surfaceVariant = Color(0xFF0E1318),
                onSurfaceVariant = Color(0xFFA9B2BC),
                outline = Color(0xFF59616A),
                outlineVariant = Color(0xFF39414A),
                primary = Color(0xFF4D9CFF),
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
