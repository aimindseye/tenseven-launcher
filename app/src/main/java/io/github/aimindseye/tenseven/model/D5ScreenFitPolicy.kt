package io.github.aimindseye.tenseven.model

/** Screen profile is measured in available display dp, not a guessed device model or scan code. */
internal object D5ScreenFitPolicy {
    data class Fit(val compact: Boolean, val dialMatchRows: Int, val monthCellDp: Int,
                   val calendarNavDp: Int, val dialCallDp: Int, val weekStripDp: Int)

    fun forViewport(widthDp: Float, heightDp: Float): Fit {
        require(widthDp > 0f && heightDp > 0f)
        val compact = heightDp < 500f || widthDp < 420f
        return Fit(compact, if (compact) 1 else 2,
            if (compact) 28 else 34, if (compact) 44 else 49,
            if (compact) 48 else 54, if (compact) 46 else 57)
    }
}
