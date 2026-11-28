package io.github.aimindseye.tenseven.design

import android.content.Context
import android.content.res.Configuration

/**
 * Colors for the companion apps that draw with plain Views (Phone, Calendar). They follow the system light or dark
 * setting, with Sable Blue as the one fixed accent. Plain ARGB ints, so the palette is unit tested off-device.
 */
class SableViewPalette private constructor(
    val bg: Int,
    val panel: Int,
    val fg: Int,
    val muted: Int,
    val line: Int,
) {
    /** Sable Blue, the same in light and dark. */
    val accent: Int = SABLE_BLUE_ARGB

    companion object {
        const val SABLE_BLUE_ARGB: Int = 0xFF2E9BD6.toInt()

        private const val WHITE = 0xFFFFFFFF.toInt()

        val dark =
            SableViewPalette(
                bg = 0xFF000000.toInt(),
                panel = 0xFF14171B.toInt(),
                fg = 0xFFF6F7FA.toInt(),
                muted = 0xFFA8AEB6.toInt(),
                line = 0xFF353A41.toInt(),
            )
        val light =
            SableViewPalette(
                bg = WHITE,
                panel = 0xFFF1F3F6.toInt(),
                fg = 0xFF14171B.toInt(),
                muted = 0xFF5B6470.toInt(),
                line = 0xFFD4D8DE.toInt(),
            )

        fun of(night: Boolean): SableViewPalette = if (night) dark else light

        fun isNight(context: Context): Boolean =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        fun forContext(context: Context): SableViewPalette = of(isNight(context))
    }
}
