package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.model.HomeDisplayPolicy
import io.github.aimindseye.tenseven.model.HomeStatusMode
import io.github.aimindseye.tenseven.platform.DeviceStats
import io.github.aimindseye.tenseven.platform.DeviceStatsFormat
import io.github.aimindseye.tenseven.platform.DeviceStatsSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val StatusTrack = Color(0xFF505D68)
private const val STATUS_SAMPLE_MS = 12_000L

/** One quiet, lifecycle-bound device readout, shared by BB7 and BB10. Never samples in Hidden mode. */
@Composable
internal fun BbHomeStatus(
    mode: HomeStatusMode,
    visible: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (mode == HomeStatusMode.Hidden) return
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember { DeviceStatsSampler() }
    var stats by remember { mutableStateOf<DeviceStats?>(null) }
    LaunchedEffect(lifecycle, mode, visible) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = withContext(Dispatchers.IO) { runCatching { sampler.sample(context) }.getOrNull() }
                delay(STATUS_SAMPLE_MS)
            }
        }
    }
    // Every selected metric is rendered. Network activity uses the existing throughput heat
    // normalization; a value of "–" means Android did not provide a reading.
    val metrics =
        listOf(
            Triple(HomeDisplayPolicy.CPU, "CPU", stats?.cpu),
            Triple(HomeDisplayPolicy.RAM, "RAM", stats?.ram),
            Triple(HomeDisplayPolicy.BATTERY, "BAT", stats?.battery),
            Triple(HomeDisplayPolicy.WIFI, "Wi-Fi", stats?.wifiDown?.let(DeviceStatsFormat::heat)),
            Triple(HomeDisplayPolicy.MOBILE, "Mobile", stats?.mobileDown?.let(DeviceStatsFormat::heat)),
            Triple(HomeDisplayPolicy.CELL, "Cell", stats?.signal),
            Triple(HomeDisplayPolicy.STORAGE, "Storage", stats?.storage),
        ).filter { it.first in visible }
    val header = rememberHeaderOptions()
    val textScale = header.statusSize.multiplier
    // Width-adaptive, but ALWAYS a single line. Seven selected metrics remain visible on
    // Titan 2; constrained viewports shrink labels and dial rings instead of wrapping.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val count = metrics.size.coerceAtLeast(1)
        val cellWidth = maxWidth / count
        val compact = metrics.size > 4 || cellWidth < 56.dp
        val diameter =
            (cellWidth - 4.dp).coerceIn(
                23.dp,
                if (header.statusSize == io.github.aimindseye.tenseven.model.HomeStatusSize.Larger) 47.dp else 43.dp,
            )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            metrics.forEach { (key, label, fraction) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    when (mode) {
                        HomeStatusMode.Dials -> {
                            ThinStatusDial(key, label, fraction, diameter, textScale)
                        }

                        HomeStatusMode.Text -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.semantics { contentDescription = "$label ${statusPercent(fraction)}" },
                            ) {
                                LineIcon(
                                    paths = statusMetricIcon(key),
                                    modifier = Modifier.size(if (compact) 13.dp else 17.dp),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    statusPercent(fraction),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = ((if (compact) 9f else 11f) * textScale).sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        HomeStatusMode.Hidden -> {
                            Unit
                        }
                    }
                }
            }
        }
    }
}

/** Minimal original outline paths; each metric retains its full accessible label. */
private fun statusMetricIcon(key: String): List<String> =
    when (key) {
        HomeDisplayPolicy.CPU -> {
            listOf(
                "M7 7h10v10H7z",
                "M10 10h4v4h-4z",
                "M3 8h4",
                "M3 12h4",
                "M3 16h4",
                "M17 8h4",
                "M17 12h4",
                "M17 16h4",
            )
        }

        HomeDisplayPolicy.RAM -> {
            listOf("M3 7h18v10H3z", "M7 10v4", "M11 10v4", "M15 10v4", "M5 17v3", "M19 17v3")
        }

        HomeDisplayPolicy.BATTERY -> {
            listOf("M3 7h16v10H3z", "M19 10h2v4", "M7 10v4", "M10 10v4")
        }

        HomeDisplayPolicy.WIFI -> {
            listOf("M3 9c5 -5 13 -5 18 0", "M6 12c4 -3 8 -3 12 0", "M9 15c2 -2 4 -2 6 0", "M12 18h.01")
        }

        HomeDisplayPolicy.MOBILE -> {
            listOf("M5 3h14v18H5z", "M9 17h6")
        }

        HomeDisplayPolicy.CELL -> {
            listOf("M4 20v-3", "M9 20v-7", "M14 20v-11", "M19 20v-16")
        }

        HomeDisplayPolicy.STORAGE -> {
            listOf("M4 7c0 -4 16 -4 16 0", "M4 7v11c0 4 16 4 16 0V7", "M4 12c0 4 16 4 16 0")
        }

        else -> {
            listOf("M4 12h16")
        }
    }

private fun shortMetricLabel(
    key: String,
    full: String,
): String =
    when (key) {
        HomeDisplayPolicy.MOBILE -> "Mob"
        HomeDisplayPolicy.STORAGE -> "Sto"
        HomeDisplayPolicy.BATTERY -> "Bat"
        HomeDisplayPolicy.WIFI -> "WiFi"
        else -> full
    }

private fun statusPercent(value: Float?): String =
    value?.takeIf { it.isFinite() }?.let { "${(it.coerceIn(0f, 1f) * 100).roundToInt()}%" } ?: "–"

@Composable
private fun ThinStatusDial(
    key: String,
    label: String,
    fraction: Float?,
    diameter: Dp,
    textScale: Float,
) {
    val progress = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.size(diameter).semantics { contentDescription = "$label ${statusPercent(progress)}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(diameter - 4.dp)) {
                drawArc(
                    color = StatusTrack.copy(alpha = 0.65f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                if (progress != null) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                statusPercent(progress).removeSuffix("%"),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = ((if (diameter < 30.dp) 8f else 10f) * textScale).sp,
                maxLines = 1,
            )
        }
        LineIcon(
            paths = statusMetricIcon(key),
            modifier =
                Modifier
                    .size(if (diameter < 30.dp) 11.dp else 14.dp)
                    .semantics { contentDescription = label },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
