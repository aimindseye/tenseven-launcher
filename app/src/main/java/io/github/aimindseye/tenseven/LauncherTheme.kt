package io.github.aimindseye.tenseven

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import androidx.compose.ui.graphics.Color
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableCustomColors
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.design.applySableSystemBars
import io.github.aimindseye.tenseven.design.rememberGlobalSableAppearance
import io.github.aimindseye.tenseven.design.resolveSableDarkAppearance
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.ui.Bb10Theme

/**
 * The appearance the launcher draws with: the theme chosen in Launcher Settings, else the BlackBerry theme. Only the
 * corner style still comes from the phone's Sable appearance, which exists only on a SableOS phone.
 */
@Composable
fun rememberLauncherAppearance(): SableAppearance {
    val context = LocalContext.current
    val global = rememberGlobalSableAppearance(context)
    val state = remember(context) { StartStateRepository(context) }
    var mode by remember { mutableStateOf(state.loadAppearanceMode()) }
    var accent by remember { mutableStateOf(state.loadAppearanceAccent()) }
    var custom by remember { mutableStateOf(customColors(state)) }
    DisposableEffect(state) {
        val stop =
            state.onAppearanceChanged {
                mode = state.loadAppearanceMode()
                accent = state.loadAppearanceAccent()
                custom = customColors(state)
            }
        onDispose(stop)
    }
    return if (mode == null && accent == null) {
        // No choice made: the BlackBerry look, pure black with the BlackBerry blue.
        SableAppearance(mode = AppearanceMode.Dark, accent = AccentPreset.Blue, cornerStyle = global.cornerStyle)
    } else {
        SableAppearance(
            mode = AppearanceMode.fromStableValue(mode),
            accent = AccentPreset.fromStableValue(accent),
            cornerStyle = global.cornerStyle,
            custom = custom,
        )
    }
}

/** The colours of the imported theme in use, or null (a built-in theme, or the imported one was removed). */
private fun customColors(state: StartStateRepository): SableCustomColors? {
    val id = state.loadActiveCustomThemeId() ?: return null
    val theme = state.loadCustomThemes().firstOrNull { it.id == id } ?: return null
    fun c(value: Int?) = value?.let { Color(it) }
    return SableCustomColors(
        accent = Color(theme.accent),
        onAccent = Color(theme.onAccent),
        background = c(theme.palette.background),
        card = c(theme.palette.card),
        tile = c(theme.palette.tile),
        line = c(theme.palette.line),
        text = c(theme.palette.text),
        muted = c(theme.palette.muted),
        dim = c(theme.palette.dim),
    )
}

/** [io.github.aimindseye.tenseven.design.SableGlobalTheme] with the launcher's own theme choice taking priority. */
@Composable
fun LauncherTheme(
    window: Window? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appearance = rememberLauncherAppearance()
    SideEffect {
        window?.let {
            applySableSystemBars(window = it, darkTheme = resolveSableDarkAppearance(context = context, appearance = appearance))
        }
    }
    // The same palette the Hub and the home pages use, so every screen Tenseven draws looks like one app.
    SableTheme(appearance = appearance) { Bb10Theme(appearance, content) }
}
