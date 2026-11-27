package io.github.aimindseye.tenseven.model

private const val FIT_COMPACT_HEIGHT_DP = 500f
private const val FIT_COMPACT_WIDTH_DP = 420f
private const val FIT_MATCH_ROWS_COMPACT = 1
private const val FIT_MATCH_ROWS_REGULAR = 2
private const val FIT_MONTH_CELL_COMPACT_DP = 28
private const val FIT_MONTH_CELL_REGULAR_DP = 34
private const val FIT_CALENDAR_NAV_COMPACT_DP = 44
private const val FIT_CALENDAR_NAV_REGULAR_DP = 49
private const val FIT_CALL_COMPACT_DP = 48
private const val FIT_CALL_REGULAR_DP = 54
private const val FIT_WEEK_STRIP_COMPACT_DP = 46
private const val FIT_WEEK_STRIP_REGULAR_DP = 57

/** Screen profile is measured in available display dp, not a guessed device model or scan code. */
object D5ScreenFitPolicy {
    data class Fit(
        val compact: Boolean,
        val dialMatchRows: Int,
        val monthCellDp: Int,
        val calendarNavDp: Int,
        val dialCallDp: Int,
        val weekStripDp: Int,
    )

    fun forViewport(
        widthDp: Float,
        heightDp: Float,
    ): Fit {
        require(widthDp > 0f && heightDp > 0f)
        val compact = heightDp < FIT_COMPACT_HEIGHT_DP || widthDp < FIT_COMPACT_WIDTH_DP
        return Fit(
            compact,
            if (compact) FIT_MATCH_ROWS_COMPACT else FIT_MATCH_ROWS_REGULAR,
            if (compact) FIT_MONTH_CELL_COMPACT_DP else FIT_MONTH_CELL_REGULAR_DP,
            if (compact) FIT_CALENDAR_NAV_COMPACT_DP else FIT_CALENDAR_NAV_REGULAR_DP,
            if (compact) FIT_CALL_COMPACT_DP else FIT_CALL_REGULAR_DP,
            if (compact) FIT_WEEK_STRIP_COMPACT_DP else FIT_WEEK_STRIP_REGULAR_DP,
        )
    }
}
