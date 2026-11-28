package io.github.aimindseye.tenseven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

private const val BURST_POINTS = 8
private const val BURST_INNER_RATIO = 0.6f

/** An eight-pointed burst around [center], used for the new-item mark on the home grid and the reference bar. */
internal fun burstPath(
    outer: Float,
    center: Offset,
): Path {
    val path = Path()
    for (i in 0 until BURST_POINTS * 2) {
        val radius = if (i % 2 == 0) outer else outer * BURST_INNER_RATIO
        val angle = Math.PI * i / BURST_POINTS - Math.PI / 2.0
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
