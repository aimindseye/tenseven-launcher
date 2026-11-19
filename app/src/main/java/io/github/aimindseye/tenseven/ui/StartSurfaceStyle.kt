package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.platform.WidgetHostController

internal data class StartSurfaceStyle(
    val appearance: SableAppearance,
    val widgetsController: WidgetHostController?,
    val homeWidgetIds: List<Int>,
    val wallpaperBehind: Boolean,
)

/** Shared root theme, safe insets and empty-space gesture boundary; owns no launcher state. */
@Composable
internal fun StartContentSurface(
    style: StartSurfaceStyle,
    destination: SableStartDestination,
    onDoubleTapEmpty: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    SableThemeWithWidgets(
        style.appearance,
        style.widgetsController,
        style.homeWidgetIds,
        if (style.wallpaperBehind) HOME_BACKDROP_ALPHA else 1f,
    ) {
        // Home, Hub and app grid must use the same safe reading scale as Settings.
        ScaledLauncherTextTheme {
            val seeThrough = style.wallpaperBehind && destination == SableStartDestination.Start
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (seeThrough) Color.Transparent else MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .pointerInput(onDoubleTapEmpty, destination) {
                                // Home only, and only empty space: taps on tiles and rows are consumed by them first.
                                if (onDoubleTapEmpty != null && destination == SableStartDestination.Start) {
                                    detectTapGestures(onDoubleTap = { onDoubleTapEmpty() })
                                }
                            },
                ) {
                    content()
                }
            }
        }
    }
}
