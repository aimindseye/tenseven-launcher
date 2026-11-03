package io.github.aimindseye.tenseven.model

/** Window and text metrics, never a guessed phone model or advertised panel resolution. */
object HomeViewport {
    private const val MAX_ROWS = 5
    private const val MIN_FRAME_WIDTH_DP = 152
    private const val FRAME_COLUMNS = 2
    private const val MAX_FONT_SCALE = 3f

    fun textScale(fontScale: Float): Float = if (fontScale.isFinite()) fontScale.coerceIn(1f, MAX_FONT_SCALE) else 1f

    fun columns(widthDp: Int, requested: Int, fontScale: Float): Int {
        val fit = (widthDp / (AppGrid.AUTO_MIN_CELL_DP * textScale(fontScale))).toInt().coerceAtLeast(1)
        return AppGrid.columnsFor(requested, widthDp).coerceAtMost(fit)
    }

    fun rows(heightDp: Int, cellDp: Int, fontScale: Float): Int =
        (heightDp / (cellDp.coerceAtLeast(1) * textScale(fontScale))).toInt().coerceIn(1, MAX_ROWS)

    fun frameColumns(widthDp: Int, fontScale: Float): Int =
        (widthDp / (MIN_FRAME_WIDTH_DP * textScale(fontScale))).toInt().coerceIn(1, FRAME_COLUMNS)
}
