package io.github.aimindseye.tenseven.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Keep a user's palette intact unless the intended foreground would become unreadable. */
internal object HubContrast {
    private const val MIN_TEXT_RATIO = 4.5f

    fun ratio(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    fun readable(surface: Color, preferred: Color): Color =
        if (ratio(surface, preferred) >= MIN_TEXT_RATIO) preferred
        else if (ratio(surface, Color.White) > ratio(surface, Color.Black)) Color.White else Color.Black

    fun colorScheme(base: ColorScheme): ColorScheme =
        base.copy(
            onSurface = readable(base.surface, base.onSurface),
            onSurfaceVariant = readable(base.surface, base.onSurfaceVariant),
            onBackground = readable(base.background, base.onBackground),
        )
}

/** Uses the selected phone/launcher palette; only repairs insufficient foreground contrast. */
@Composable
internal fun HubContrastTheme(content: @Composable () -> Unit) {
    val original = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = HubContrast.colorScheme(original),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
