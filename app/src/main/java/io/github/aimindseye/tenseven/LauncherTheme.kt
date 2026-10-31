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
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.design.applySableSystemBars
import io.github.aimindseye.tenseven.design.rememberGlobalSableAppearance
import io.github.aimindseye.tenseven.design.resolveSableDarkAppearance
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * The appearance the launcher draws with: the theme chosen in Launcher Settings when there is one, otherwise the
 * phone's Sable appearance. The global provider only exists on a SableOS phone, so on an ordinary install the choice
 * made here is the one that counts.
 */
@Composable
fun rememberLauncherAppearance(): SableAppearance {
    val context = LocalContext.current
    val global = rememberGlobalSableAppearance(context)
    val state = remember(context) { StartStateRepository(context) }
    var mode by remember { mutableStateOf(state.loadAppearanceMode()) }
    var accent by remember { mutableStateOf(state.loadAppearanceAccent()) }
    DisposableEffect(state) {
        val stop =
            state.onAppearanceChanged {
                mode = state.loadAppearanceMode()
                accent = state.loadAppearanceAccent()
            }
        onDispose(stop)
    }
    return if (mode == null && accent == null) {
        global
    } else {
        SableAppearance(
            mode = AppearanceMode.fromStableValue(mode),
            accent = AccentPreset.fromStableValue(accent),
            cornerStyle = global.cornerStyle,
        )
    }
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
    SableTheme(appearance = appearance, content = content)
}
