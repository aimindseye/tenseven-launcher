package io.github.aimindseye.tenseven.model

private object TextScale {
    const val SMALL = 0.90f
    const val DEFAULT = 1.0f
    const val LARGE = 1.10f
}

/** Preserve exact WMO weather-code to label mapping used by the launcher. */
private object WmoCode {
    const val CLEAR = 0
    const val MAINLY_CLEAR = 1
    const val PARTLY_CLOUDY = 2
    const val OVERCAST = 3
    const val FOG = 45
    const val RIME_FOG = 48
    const val DRIZZLE_LIGHT = 51
    const val DRIZZLE_MODERATE = 53
    const val DRIZZLE_DENSE = 55
    const val FREEZING_DRIZZLE_LIGHT = 56
    const val FREEZING_DRIZZLE_DENSE = 57
    const val RAIN_SLIGHT = 61
    const val RAIN_MODERATE = 63
    const val RAIN_HEAVY = 65
    const val FREEZING_RAIN_LIGHT = 66
    const val FREEZING_RAIN_HEAVY = 67
    const val SNOW_SLIGHT = 71
    const val SNOW_MODERATE = 73
    const val SNOW_HEAVY = 75
    const val SNOW_GRAINS = 77
    const val SHOWERS_SLIGHT = 80
    const val SHOWERS_MODERATE = 81
    const val SHOWERS_VIOLENT = 82
    const val SNOW_SHOWERS_SLIGHT = 85
    const val SNOW_SHOWERS_HEAVY = 86
    const val THUNDERSTORM = 95
    const val THUNDER_HAIL_SLIGHT = 96
    const val THUNDER_HAIL_HEAVY = 99
}

/** Bounded launcher-owned typography; never changes Android's system font size. */
enum class LauncherTextSize(
    val multiplier: Float,
) {
    Small(TextScale.SMALL),
    Default(TextScale.DEFAULT),
    Large(TextScale.LARGE),
    ;

    companion object {
        fun decode(value: String?): LauncherTextSize = entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Compact WMO descriptions to avoid wrapping status/date header text. */
object HomeWeatherCodes {
    fun description(code: Int): String =
        when (code) {
            WmoCode.CLEAR -> "Clear"

            WmoCode.MAINLY_CLEAR, WmoCode.PARTLY_CLOUDY -> "Partly cloudy"

            WmoCode.OVERCAST -> "Cloudy"

            WmoCode.FOG, WmoCode.RIME_FOG -> "Fog"

            WmoCode.DRIZZLE_LIGHT,
            WmoCode.DRIZZLE_MODERATE,
            WmoCode.DRIZZLE_DENSE,
            WmoCode.FREEZING_DRIZZLE_LIGHT,
            WmoCode.FREEZING_DRIZZLE_DENSE,
            -> "Drizzle"

            WmoCode.RAIN_SLIGHT,
            WmoCode.RAIN_MODERATE,
            WmoCode.RAIN_HEAVY,
            WmoCode.FREEZING_RAIN_LIGHT,
            WmoCode.FREEZING_RAIN_HEAVY,
            -> "Rain"

            WmoCode.SNOW_SLIGHT,
            WmoCode.SNOW_MODERATE,
            WmoCode.SNOW_HEAVY,
            WmoCode.SNOW_GRAINS,
            -> "Snow"

            WmoCode.SHOWERS_SLIGHT, WmoCode.SHOWERS_MODERATE, WmoCode.SHOWERS_VIOLENT -> "Showers"

            WmoCode.SNOW_SHOWERS_SLIGHT, WmoCode.SNOW_SHOWERS_HEAVY -> "Snow showers"

            WmoCode.THUNDERSTORM, WmoCode.THUNDER_HAIL_SLIGHT, WmoCode.THUNDER_HAIL_HEAVY -> "Storm"

            else -> "Weather"
        }
}
