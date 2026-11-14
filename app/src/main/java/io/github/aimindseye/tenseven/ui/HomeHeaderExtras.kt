package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.model.LauncherTextSize
import io.github.aimindseye.tenseven.platform.HomeWeatherClient
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.delay
import java.util.Locale

/** Home reads new settings on resume; it does not require a launcher restart. */
@Composable
internal fun rememberHeaderOptions(): HeaderOptions {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repository = remember(context) { StartStateRepository(context) }

    fun read() =
        HeaderOptions(
            repository.loadLauncherTextSize(),
            repository.loadHomeWeatherEnabled(),
            repository.loadHomeWeatherCity(),
            repository.loadHomeWeatherUseDeviceLocation(),
            repository.loadHomeStatusSize(),
        )
    var current by remember(context) { mutableStateOf(read()) }
    DisposableEffect(lifecycle, repository) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) current = read()
            }
        lifecycle.addObserver(observer)
        // Preference changes must update active settings too, not only after leaving the Activity.
        val stop = repository.onHomeReadabilityChanged { current = read() }
        onDispose {
            stop()
            lifecycle.removeObserver(observer)
        }
    }
    return current
}

internal fun TextUnit.safeLauncherScale(value: LauncherTextSize): TextUnit = this * value.multiplier

/** Current conditions are optional; no placeholder or made-up temperature is displayed. */
@Suppress("FunctionNaming")
@Composable
internal fun HomeWeatherLine(
    options: HeaderOptions,
    modifier: Modifier = Modifier,
) {
    if (!options.showWeather || (!options.useDeviceLocation && options.city.isBlank())) return
    val context = LocalContext.current.applicationContext
    val locale = Locale.getDefault()
    val fahrenheit = locale.country.equals("US", ignoreCase = true)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = remember(context) { HomeWeatherClient(context) }
    val weather by produceState<io.github.aimindseye.tenseven.platform.HomeWeatherObservation?>(
        initialValue = null,
        options.city,
        options.useDeviceLocation,
        fahrenheit,
        lifecycle,
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value =
                    if (options.useDeviceLocation) {
                        client.currentNearDevice(fahrenheit)
                            ?: options.city.takeIf { it.isNotBlank() }?.let { client.current(it, fahrenheit) }
                    } else {
                        client.current(options.city, fahrenheit)
                    }
                delay(HomeWeatherClient.CACHE_LIFETIME_MS)
            }
        }
    }
    val conditions = weather ?: return
    val uriHandler = LocalUriHandler.current
    // Display actual locality if Android's coarse-location geocoder provided it.
    // If unavailable, be transparent instead of claiming an invented nearby city.
    val locality =
        if (options.useDeviceLocation) {
            conditions.locationName ?: options.city.trim().takeIf { it.isNotBlank() } ?: "Approximate location"
        } else {
            options.city.trim().ifBlank { "Selected city" }
        }
    Column(modifier = modifier) {
        Text(
            "$locality · ${conditions.temperature}${conditions.unit} ${conditions.condition}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (11f * options.size.multiplier).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Attribution remains visible and tappable but does not replace the location name.
        Text(
            "Open-Meteo",
            modifier = Modifier.clickable { uriHandler.openUri("https://open-meteo.com/") },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (9f * options.size.multiplier).sp),
            maxLines = 1,
        )
    }
}

/** Scale only reading typography. Compact nav labels, indicators and system text keep original sizes. */
@Suppress("FunctionNaming")
@Composable
internal fun ScaledLauncherTextTheme(content: @Composable () -> Unit) {
    val multiplier = rememberHeaderOptions().size.multiplier
    val base = MaterialTheme.typography
    MaterialTheme(
        typography =
            base.copy(
                headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * multiplier),
                titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * multiplier),
                titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * multiplier),
                bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * multiplier),
                bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * multiplier),
                bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * multiplier),
                labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * multiplier),
                labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * multiplier),
                labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * multiplier),
                titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * multiplier),
            ),
        content = content,
    )
}
