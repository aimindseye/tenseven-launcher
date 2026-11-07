package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import io.github.aimindseye.tenseven.design.SableAppearance

internal val SableBlue = Color(0xFF4D9CFF)
internal val SableGreen = Color(0xFF35C66B)
internal val SablePurple = Color(0xFF7A42E8)
internal val SableSlate = Color(0xFF64748B)
internal val SableOrange = Color(0xFFF28C45)
internal val SableSun = Color(0xFFF5C542)
internal val SableTilePalette =
    listOf(
        SableBlue,
        SableGreen,
        SablePurple,
        SableOrange,
        SableSlate,
    )

// BlackBerry 10 look on the Start pages. Values from the public BB10 look; the UI
// Guidelines PDF has the exact tokens. The pages follow Android's Dark theme and the
// Sable accent: dark is the BB10 look, light keeps the layout on light surfaces, and the
// focus highlight is the accent (BB10 blue for the default Sable Blue).
internal val Bb10DefaultBlue = Color(0xFF2E9BD6)

/** Start's page colours for one Dark theme state and accent. */
@Immutable
internal data class StartPalette(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val card: Color,
    val tile: Color,
    val line: Color,
    val barTop: Color,
    val barBottom: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
)

internal fun startPalette(
    dark: Boolean,
    accent: AccentPreset,
): StartPalette {
    val accentColor = if (accent == AccentPreset.Blue) Bb10DefaultBlue else accent.color
    val onAccent = if (accent == AccentPreset.Blue) Color.White else accent.onColor
    return if (dark) {
        StartPalette(
            accent = accentColor,
            onAccent = onAccent,
            // Pure black, the default Dark theme on the Q25 (owner request, 2026-10-28): the
            // pages, Hub and All Apps are #000000 and cards are a step above it.
            background = Color.Black,
            card = Color(0xFF141414),
            tile = Color(0xFF1C1C1C),
            line = Color(0xFF262626),
            barTop = Color(0xFF1C1C1C),
            barBottom = Color.Black,
            text = Color.White,
            muted = Color(0xFFA6A6A6),
            dim = Color(0xFF5E5E5E),
        )
    } else {
        StartPalette(
            accent = accentColor,
            onAccent = onAccent,
            background = Color(0xFFF2F4F7),
            card = Color.White,
            tile = Color(0xFFE6E9EE),
            line = Color(0xFFCBD1D8),
            barTop = Color(0xFFF7F8FA),
            barBottom = Color(0xFFDDE1E6),
            text = Color(0xFF12171C),
            muted = Color(0xFF515B66),
            dim = Color(0xFFA3ABB4),
        )
    }
}

/** An imported theme's palette: the colours its file sets over the default for the light or dark mode. */
internal fun customPalette(
    custom: io.github.aimindseye.tenseven.design.SableCustomColors,
    dark: Boolean,
): StartPalette {
    val base = startPalette(dark, AccentPreset.Blue)
    return base.copy(
        accent = custom.accent,
        onAccent = custom.onAccent,
        background = custom.background ?: base.background,
        card = custom.card ?: base.card,
        tile = custom.tile ?: base.tile,
        line = custom.line ?: base.line,
        barTop = custom.tile ?: base.barTop,
        barBottom = custom.background ?: base.barBottom,
        text = custom.text ?: base.text,
        muted = custom.muted ?: base.muted,
        dim = custom.dim ?: base.dim,
    )
}

/** The BlackBerry 10 pages in the phone's own colours: the Material You scheme already set up by the theme. */
internal fun phonePalette(scheme: androidx.compose.material3.ColorScheme): StartPalette =
    StartPalette(
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        background = scheme.background,
        card = scheme.surfaceVariant.copy(alpha = 0.55f).compositeOver(scheme.background),
        tile = scheme.surfaceVariant,
        line = scheme.outlineVariant,
        barTop = scheme.surfaceVariant,
        barBottom = scheme.background,
        text = scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        dim = scheme.outline,
    )

internal val LocalStartPalette = staticCompositionLocalOf { startPalette(dark = true, accent = AccentPreset.Blue) }

internal val Bb10Blue: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.accent
internal val Bb10OnBlue: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.onAccent
internal val Bb10Background: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.background
internal val Bb10Card: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.card
internal val Bb10Tile: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.tile
internal val Bb10Line: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.line
internal val Bb10BarTop: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.barTop
internal val Bb10BarBottom: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.barBottom
internal val Bb10Text: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.text
internal val Bb10Muted: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.muted
internal val Bb10Dim: Color
    @Composable @ReadOnlyComposable
    get() = LocalStartPalette.current.dim

@Composable
internal fun Bb10Theme(
    appearance: SableAppearance,
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.mode) {
            AppearanceMode.FollowSystem -> isSystemInDarkTheme()
            AppearanceMode.Light -> false
            AppearanceMode.Dark -> true
        }
    val base = MaterialTheme.colorScheme
    val palette =
        remember(dark, appearance.accent, appearance.custom, base) {
            val custom = appearance.custom
            if (custom != null) {
                customPalette(custom, dark)
            } else if (appearance.accent == AccentPreset.Phone) {
                phonePalette(base)
            } else {
                startPalette(dark, appearance.accent)
            }
        }
    CompositionLocalProvider(LocalStartPalette provides palette) {
        MaterialTheme(
            colorScheme =
                base.copy(
                    primary = palette.accent,
                    onPrimary = palette.onAccent,
                    background = palette.background,
                    onBackground = palette.text,
                    surface = palette.card,
                    onSurface = palette.text,
                    surfaceVariant = palette.tile,
                    onSurfaceVariant = palette.muted,
                    outlineVariant = palette.line,
                ),
            typography =
                if (dark && appearance.accent == AccentPreset.Blue && appearance.custom == null) {
                    io.github.aimindseye.tenseven.design
                        .blackBerryTypography(MaterialTheme.typography)
                } else {
                    MaterialTheme.typography
                },
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

// The width in dp the app grid has: the screen width less the grid's padding on both sides.
