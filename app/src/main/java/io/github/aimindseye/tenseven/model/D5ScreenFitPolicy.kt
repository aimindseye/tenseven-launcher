package io.github.aimindseye.tenseven.model

private object FitLimits {
    const val COMPACT_HEIGHT_DP = 500f
    const val COMPACT_WIDTH_DP = 420f
    const val MATCH_ROWS_COMPACT = 1
    const val MATCH_ROWS_REGULAR = 2
    const val MONTH_CELL_COMPACT_DP = 28
    const val MONTH_CELL_REGULAR_DP = 34
    const val CALENDAR_NAV_COMPACT_DP = 44
    const val CALENDAR_NAV_REGULAR_DP = 49
    const val CALL_COMPACT_DP = 48
    const val CALL_REGULAR_DP = 54
    const val WEEK_STRIP_COMPACT_DP = 46
    const val WEEK_STRIP_REGULAR_DP = 57
}

/** Screen profile is measured in available display dp, not a guessed device model or scan code. */
internal object D5ScreenFitPolicy {
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
        val compact = heightDp < FitLimits.COMPACT_HEIGHT_DP || widthDp < FitLimits.COMPACT_WIDTH_DP
        return Fit(
            compact,
            if (compact) FitLimits.MATCH_ROWS_COMPACT else FitLimits.MATCH_ROWS_REGULAR,
            if (compact) FitLimits.MONTH_CELL_COMPACT_DP else FitLimits.MONTH_CELL_REGULAR_DP,
            if (compact) FitLimits.CALENDAR_NAV_COMPACT_DP else FitLimits.CALENDAR_NAV_REGULAR_DP,
            if (compact) FitLimits.CALL_COMPACT_DP else FitLimits.CALL_REGULAR_DP,
            if (compact) FitLimits.WEEK_STRIP_COMPACT_DP else FitLimits.WEEK_STRIP_REGULAR_DP,
        )
    }
}
