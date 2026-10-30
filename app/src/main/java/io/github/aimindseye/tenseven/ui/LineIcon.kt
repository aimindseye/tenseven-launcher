package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addSvg
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** Plain one-colour line icons (24 x 24 box, 2 unit round stroke, Tabler outline geometry, MIT) for the status row. */
object LineIconPaths {
    val BELL =
        listOf(
            "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6",
            "M9 17v1a3 3 0 0 0 6 0v-1",
        )
    private const val SPEAKER = "M6 15h-2a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h2l3.5 -4.5a.8 .8 0 0 1 1.5 .5v14a.8 .8 0 0 1 -1.5 .5l-3.5 -4.5"
    val SOUND = listOf(SPEAKER, "M15 8a5 5 0 0 1 0 8", "M17.7 5a9 9 0 0 1 0 14")
    val VIBRATE =
        listOf(
            "M3 9l2 3l-2 3",
            "M21 9l-2 3l2 3",
            "M8 5h8a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-8a1 1 0 0 1 -1 -1v-12a1 1 0 0 1 1 -1",
        )
    val SILENT = listOf(SPEAKER, "M16 10l4 4m0 -4l-4 4")
}

@Composable
internal fun LineIcon(
    paths: List<String>,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val parsed = remember(paths) { paths.mapNotNull { d -> runCatching { Path().apply { addSvg(d) } }.getOrNull() } }
    Canvas(modifier = modifier) {
        val scale = size.minDimension / VIEWPORT
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        translate(left = (size.width - size.minDimension) / 2f, top = (size.height - size.minDimension) / 2f) {
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                parsed.forEach { drawPath(path = it, color = tint, style = stroke) }
            }
        }
    }
}

private const val VIEWPORT = 24f
private const val STROKE = 2f
