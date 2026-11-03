package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.platform.PlatformCapabilities
import io.github.aimindseye.tenseven.platform.PlatformCapabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Presenters/tests can inject a complete immutable capability snapshot. */
internal val LocalPlatformCapabilities = staticCompositionLocalOf<PlatformCapabilities?> { null }

/** Refresh after return, profile/permission signals and while unlocked Home is visible; no background polling. */
@Composable
internal fun rememberPlatformCapabilities(refreshGeneration: Int = 0): PlatformCapabilities {
    LocalPlatformCapabilities.current?.let { return it }
    val app = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val value by produceState(PlatformCapabilityProbe.detect(app), app, lifecycle, refreshGeneration) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = withContext(Dispatchers.IO) { PlatformCapabilityProbe.detect(app) }
                delay(2_000)
            }
        }
    }
    return value
}
