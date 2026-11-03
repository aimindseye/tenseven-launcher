package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import kotlinx.coroutines.delay

/** Live repository polling belongs to resumed Home; its last result survives screen changes. */
@Composable
internal fun rememberLiveSurface(
    repository: LiveSurfaceRepository,
    refreshGeneration: Int,
    homeGeneration: Int,
): Pair<LiveSurfaceSnapshot, Boolean> {
    var snapshot by remember { mutableStateOf(repository.initialSnapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(refreshGeneration, homeGeneration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshing = true
                try {
                    snapshot = repository.snapshot()
                } finally {
                    refreshing = false
                }
                delay(LIVE_REFRESH_INTERVAL_MS)
            }
        }
    }
    return snapshot to refreshing
}

private const val LIVE_REFRESH_INTERVAL_MS = 5_000L
