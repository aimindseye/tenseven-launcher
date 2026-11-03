package io.github.aimindseye.tenseven.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.platform.WidgetHostController
import io.github.aimindseye.tenseven.platform.WidgetProfileEpoch
import kotlinx.coroutines.delay

/** Drop hosted content on pause/lock; keep the allocated widget id for profile unlock or provider recovery. */
@Composable
internal fun rememberWidgetInfo(
    controller: WidgetHostController,
    id: Int,
): AppWidgetProviderInfo? {
    val locked = rememberPlatformCapabilities().deviceLocked
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val epoch by WidgetProfileEpoch.revisions.collectAsStateWithLifecycle()
    val initial: Pair<Int, AppWidgetProviderInfo?> = epoch to null
    val info by produceState(initial, controller, id, lifecycle, locked, epoch) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    value = epoch to if (locked) null else controller.info(id)
                    delay(WIDGET_CHECK_INTERVAL_MS)
                }
            } finally {
                value = epoch to null
            }
        }
    }
    return info.takeIf { it.first == epoch && !locked }?.second
}

private const val WIDGET_CHECK_INTERVAL_MS = 2_000L
