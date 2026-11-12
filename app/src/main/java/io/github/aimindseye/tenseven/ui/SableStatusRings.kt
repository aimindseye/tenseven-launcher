// Ring drawing ported from Titan AI (https://github.com/andrehafner/aiassistant,
// ui/LauncherScreens.kt StatRing/NetRing), Copyright (c) 2026 Andre Hafner,
// MIT License. See THIRD_PARTY_NOTICES.md. Sable keeps the eased value change
// and drops Titan AI's always-running shimmer and glow pulse to save battery.

package io.github.aimindseye.tenseven.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.platform.DeviceStats
import io.github.aimindseye.tenseven.platform.DeviceStatsFormat
import io.github.aimindseye.tenseven.platform.DeviceStatsSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private object RingMetrics {
    const val DEFAULT_SIZE_DP = 36
    const val SPACING_DP = 6
    const val LOW_BATTERY = 0.2f
    const val PERCENT_SCALE = 100
    const val SIGNAL_BARS = 4
    const val WARNING_THRESHOLD = 0.85f
    const val ANIMATION_MS = 900
    const val TRACK_ALPHA = 0.18f
    const val STROKE_RATIO = 0.11f
    const val START_DEGREES = 135f
    const val SWEEP_DEGREES = 270f
    const val VISIBLE_ARC_THRESHOLD = 0.005f
    const val GLOW_ALPHA = 0.22f
    const val GLOW_STROKE_SCALE = 1.9f
    const val CAP_ALPHA = 0.9f
    const val THIN_STROKE_SCALE = 0.45f
    const val INNER_STROKE_OFFSET = 1.15f
    const val LABEL_OFFSET_DP = 3
    const val SMALL_TEXT_SP = 9
    const val TEXT_SP = 11
    const val SMALL_LINE_SP = 10
    const val LINE_SP = 12
    const val LABEL_TEXT_SP = 10
    const val DISABLED_WARNING_THRESHOLD = 2f
}

private val RingGreen = Color(0xFF35C66B)
private val RingPurple = Color(0xFF7A42E8)
private val RingOrange = Color(0xFFF28C45)
private const val SAMPLE_INTERVAL_MS = 3_000L

/**
 * Live device rings for the Start header: Wi-Fi, Mobile, CPU, RAM, Battery
 * and Cell. Samples every three seconds while HOME is started and stops when
 * it is not, so the rings cost nothing behind another app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SableStatusRings(
    modifier: Modifier = Modifier,
    ringSize: Dp = RingMetrics.DEFAULT_SIZE_DP.dp,
    visible: Set<String> = io.github.aimindseye.tenseven.model.HomeDisplayPolicy.DEFAULT_DIALS,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember { DeviceStatsSampler() }
    var stats by remember { mutableStateOf<DeviceStats?>(null) }

    LaunchedEffect(lifecycle, sampler) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = withContext(Dispatchers.IO) { sampler.sample(context) }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    val st = stats ?: return
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RingMetrics.SPACING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(RingMetrics.SPACING_DP.dp),
    ) {
        if ("wifi" in visible) NetRing(label = "Wi-Fi", up = st.wifiUp, down = st.wifiDown, size = ringSize)
        if ("mobile" in visible) NetRing(label = t("mobile"), up = st.mobileUp, down = st.mobileDown, size = ringSize)
        if ("cpu" in visible) StatRing(label = "CPU", fraction = st.cpu, color = primary, size = ringSize)
        if ("ram" in visible) StatRing(label = "RAM", fraction = st.ram, color = RingPurple, size = ringSize)
        if ("battery" in visible) batteryStatusRing(st, ringSize, error)
        if ("storage" in visible) {
            StatRing(
                label = t("storage"),
                fraction = st.storage,
                color = RingPurple,
                size = ringSize,
                center = listOf(st.storage?.let { "${(it * RingMetrics.PERCENT_SCALE).roundToInt()}%" } ?: "–"),
            )
        }
        if ("cell" in visible) {
            StatRing(
                label = t("cell"),
                fraction = st.signal,
                color = RingOrange,
                size = ringSize,
                center = listOf(st.signal?.let { "${(it * RingMetrics.SIGNAL_BARS).roundToInt()}/4" } ?: "–"),
                hotAbove = RingMetrics.DISABLED_WARNING_THRESHOLD,
            )
        }
    }
}

private val batteryStatusRing: @Composable (DeviceStats, Dp, Color) -> Unit = { st, ringSize, error ->
    StatRing(
        label =
            when {
                st.charging -> t("bat")
                st.hoursLeft != null -> DeviceStatsFormat.hours(st.hoursLeft) + " left"
                else -> "BAT"
            },
        fraction = st.battery,
        color = if (st.battery <= RingMetrics.LOW_BATTERY && !st.charging) error else RingGreen,
        size = ringSize,
        hotAbove = RingMetrics.DISABLED_WARNING_THRESHOLD,
    )
}

/**
 * A 270° gauge: a dim track, an arc that eases to each new sample, a bright
 * cap at its tip and the value in the middle. A null fraction shows a dash.
 */
@Composable
private fun StatRing(
    label: String,
    fraction: Float?,
    color: Color,
    size: Dp,
    /** A thinner arc just inside the first (upload under download). */
    innerFraction: Float? = null,
    innerColor: Color = color,
    /** Replaces the percentage in the middle; two short lines at most. */
    center: List<String>? = null,
    /** Fraction above which the arc turns the error colour; above 1 never. */
    hotAbove: Float = RingMetrics.WARNING_THRESHOLD,
) {
    val value by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = RingMetrics.ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "ring",
    )
    val innerValue by animateFloatAsState(
        targetValue = (innerFraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = RingMetrics.ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "inner",
    )
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = RingMetrics.TRACK_ALPHA)
    val arcColor = if (fraction != null && fraction > hotAbove) MaterialTheme.colorScheme.error else color
    val textColor = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val centerLines =
        center
            ?: listOf(if (fraction == null) "–" else "${(value * RingMetrics.PERCENT_SCALE).roundToInt()}")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$label ${centerLines.joinToString(" ")}" },
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val arcState =
                RingArcState(
                    ringSize = size,
                    fraction = fraction,
                    value = value,
                    innerFraction = innerFraction,
                    innerValue = innerValue,
                    track = track,
                    arcColor = arcColor,
                    innerColor = innerColor,
                )
            Canvas(Modifier.fillMaxSize()) { drawRingArcs(arcState) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val twoLines = centerLines.size > 1
                centerLines.take(2).forEachIndexed { i, line ->
                    Text(
                        text = line,
                        fontSize = if (twoLines) RingMetrics.SMALL_TEXT_SP.sp else RingMetrics.TEXT_SP.sp,
                        lineHeight = if (twoLines) RingMetrics.SMALL_LINE_SP.sp else RingMetrics.LINE_SP.sp,
                        color = if (i == 0 && fraction != null) textColor else muted,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = RingMetrics.LABEL_TEXT_SP.sp,
            color = muted,
            maxLines = 1,
            modifier = Modifier.offset(y = (-RingMetrics.LABEL_OFFSET_DP).dp),
        )
    }
}

private data class RingArcState(
    val ringSize: Dp,
    val fraction: Float?,
    val value: Float,
    val innerFraction: Float?,
    val innerValue: Float,
    val track: Color,
    val arcColor: Color,
    val innerColor: Color,
)

private fun DrawScope.drawRingArcs(spec: RingArcState) {
    val stroke = spec.ringSize.toPx() * RingMetrics.STROKE_RATIO
    val inset = stroke / 2 + 1.dp.toPx()
    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
    val topLeft = Offset(inset, inset)
    val startAngle = RingMetrics.START_DEGREES
    val sweepMax = RingMetrics.SWEEP_DEGREES
    drawArc(
        spec.track,
        startAngle,
        sweepMax,
        false,
        topLeft,
        arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawOuterRing(spec, RingGeometry(stroke, arcSize, topLeft, startAngle, sweepMax))
    drawInnerRing(spec, stroke, inset, startAngle, sweepMax)
}

private data class RingGeometry(
    val stroke: Float,
    val arcSize: Size,
    val topLeft: Offset,
    val startAngle: Float,
    val sweepMax: Float,
)

private fun DrawScope.drawOuterRing(
    spec: RingArcState,
    geometry: RingGeometry,
) {
    val stroke = geometry.stroke
    val arcSize = geometry.arcSize
    val topLeft = geometry.topLeft
    val startAngle = geometry.startAngle
    val sweepMax = geometry.sweepMax
    if (spec.fraction != null && spec.value > RingMetrics.VISIBLE_ARC_THRESHOLD) {
        val sweep = sweepMax * spec.value
        drawArc(
            spec.arcColor.copy(alpha = RingMetrics.GLOW_ALPHA),
            startAngle,
            sweep,
            false,
            topLeft,
            arcSize,
            style = Stroke(stroke * RingMetrics.GLOW_STROKE_SCALE, cap = StrokeCap.Round),
        )
        drawArc(
            spec.arcColor,
            startAngle,
            sweep,
            false,
            topLeft,
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val rad = Math.toRadians((startAngle + sweep).toDouble())
        val r = arcSize.width / 2
        drawCircle(
            Color.White.copy(alpha = RingMetrics.CAP_ALPHA),
            radius = stroke * RingMetrics.THIN_STROKE_SCALE,
            center =
                Offset(
                    this.size.width / 2 + (r * cos(rad)).toFloat(),
                    this.size.height / 2 + (r * sin(rad)).toFloat(),
                ),
        )
    }
}

private fun DrawScope.drawInnerRing(
    spec: RingArcState,
    stroke: Float,
    inset: Float,
    startAngle: Float,
    sweepMax: Float,
) {
    if (spec.innerFraction != null) {
        val innerInset = inset + stroke * RingMetrics.INNER_STROKE_OFFSET
        val innerSize = Size(this.size.width - innerInset * 2, this.size.height - innerInset * 2)
        val innerTopLeft = Offset(innerInset, innerInset)
        val innerStroke = Stroke(stroke * RingMetrics.THIN_STROKE_SCALE, cap = StrokeCap.Round)
        drawArc(spec.track, startAngle, sweepMax, false, innerTopLeft, innerSize, style = innerStroke)
        if (spec.innerValue > RingMetrics.VISIBLE_ARC_THRESHOLD) {
            drawArc(
                spec.innerColor.copy(alpha = RingMetrics.CAP_ALPHA),
                startAngle,
                sweepMax * spec.innerValue,
                false,
                innerTopLeft,
                innerSize,
                style = innerStroke,
            )
        }
    }
}

/**
 * Throughput: the outer arc is download, the thin inner arc upload, both on a
 * log scale that fills at about 30 MiB/s.
 */
@Composable
private fun NetRing(
    label: String,
    up: Long?,
    down: Long?,
    size: Dp,
) {
    StatRing(
        label = label,
        fraction = if (down == null && up == null) null else DeviceStatsFormat.heat(down),
        color = RingGreen,
        size = size,
        innerFraction = DeviceStatsFormat.heat(up),
        innerColor = MaterialTheme.colorScheme.primary,
        center = listOf("↓" + DeviceStatsFormat.rate(down), "↑" + DeviceStatsFormat.rate(up)),
        hotAbove = RingMetrics.DISABLED_WARNING_THRESHOLD,
    )
}
