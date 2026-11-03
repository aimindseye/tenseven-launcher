package io.github.aimindseye.tenseven.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.platform.WidgetHostController
import kotlinx.coroutines.delay

/** Drop hosted content on pause/lock; keep the allocated widget id for profile unlock or provider recovery. */
@Composable
internal fun rememberWidgetInfo(controller: WidgetHostController, id: Int): AppWidgetProviderInfo? {
    val locked = rememberPlatformCapabilities().deviceLocked
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val info by produceState<AppWidgetProviderInfo?>(null, controller, id, lifecycle, locked) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    value = if (locked) null else controller.info(id)
                    delay(WIDGET_CHECK_INTERVAL_MS)
                }
            } finally {
                value = null
            }
        }
    }
    return info
}

private const val WIDGET_CHECK_INTERVAL_MS = 2_000L
