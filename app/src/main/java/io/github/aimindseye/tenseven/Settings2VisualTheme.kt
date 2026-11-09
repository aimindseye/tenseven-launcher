package io.github.aimindseye.tenseven

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import io.github.aimindseye.tenseven.design.SableAppearance

/**
 * One Settings/Setup surface palette. No launcher Home, Peek, Active Frames source, or system UI
 * is altered. The BB10 default uses black/white with distinct blue borders rather than legacy
 * cyan-filled emphasis; selected nondefault palettes keep their user-specified accent.
 */
@Composable
internal fun Settings2VisualTheme(
    appearance: SableAppearance,
    content: @Composable () -> Unit,
) {
    val classic =
        appearance.mode == AppearanceMode.Dark &&
            appearance.accent == AccentPreset.Blue &&
            appearance.custom == null
    val current = MaterialTheme.colorScheme
    val selectedAccent = if (classic) Color(0xFF4D9CFF) else current.primary
    MaterialTheme(
        colorScheme =
            current.copy(
                primary = selectedAccent,
                onPrimary = if (classic) Color.White else current.onPrimary,
                // Thin neutral outlines for unselected rows; only focus/selection is blue.
                outlineVariant = if (classic) Color(0xFF444A52) else current.outlineVariant,
                surfaceVariant = if (classic) Color(0xFF0D1115) else current.surfaceVariant,
            ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
