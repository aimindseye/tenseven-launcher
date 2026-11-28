package io.github.aimindseye.tenseven.model

private const val SCALE_SMALL = 0.90f
private const val SCALE_DEFAULT = 1.0f
private const val SCALE_LARGE = 1.10f

/** Preserve exact WMO weather-code to label mapping used by the launcher. */
private const val WMO_CLEAR = 0
private const val WMO_MAINLY_CLEAR = 1
private const val WMO_PARTLY_CLOUDY = 2
private const val WMO_OVERCAST = 3
private const val WMO_FOG = 45
private const val WMO_RIME_FOG = 48
private const val WMO_DRIZZLE_LIGHT = 51
private const val WMO_DRIZZLE_MODERATE = 53
private const val WMO_DRIZZLE_DENSE = 55
private const val WMO_FREEZING_DRIZZLE_LIGHT = 56
private const val WMO_FREEZING_DRIZZLE_DENSE = 57
private const val WMO_RAIN_SLIGHT = 61
private const val WMO_RAIN_MODERATE = 63
private const val WMO_RAIN_HEAVY = 65
private const val WMO_FREEZING_RAIN_LIGHT = 66
private const val WMO_FREEZING_RAIN_HEAVY = 67
private const val WMO_SNOW_SLIGHT = 71
private const val WMO_SNOW_MODERATE = 73
private const val WMO_SNOW_HEAVY = 75
private const val WMO_SNOW_GRAINS = 77
private const val WMO_SHOWERS_SLIGHT = 80
private const val WMO_SHOWERS_MODERATE = 81
private const val WMO_SHOWERS_VIOLENT = 82
private const val WMO_SNOW_SHOWERS_SLIGHT = 85
private const val WMO_SNOW_SHOWERS_HEAVY = 86
private const val WMO_THUNDERSTORM = 95
private const val WMO_THUNDER_HAIL_SLIGHT = 96
private const val WMO_THUNDER_HAIL_HEAVY = 99

/** Bounded launcher-owned typography; never changes Android's system font size. */
enum class LauncherTextSize(
    val multiplier: Float,
) {
    Small(SCALE_SMALL),
    Default(SCALE_DEFAULT),
    Large(SCALE_LARGE),
    ;

    companion object {
        fun decode(value: String?): LauncherTextSize = entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Compact WMO descriptions to avoid wrapping status/date header text. */
object HomeWeatherCodes {
    fun description(code: Int): String =
        when (code) {
            WMO_CLEAR -> "Clear"

            WMO_MAINLY_CLEAR, WMO_PARTLY_CLOUDY -> "Partly cloudy"

            WMO_OVERCAST -> "Cloudy"

            WMO_FOG, WMO_RIME_FOG -> "Fog"

            WMO_DRIZZLE_LIGHT,
            WMO_DRIZZLE_MODERATE,
            WMO_DRIZZLE_DENSE,
            WMO_FREEZING_DRIZZLE_LIGHT,
            WMO_FREEZING_DRIZZLE_DENSE,
            -> "Drizzle"

            WMO_RAIN_SLIGHT,
            WMO_RAIN_MODERATE,
            WMO_RAIN_HEAVY,
            WMO_FREEZING_RAIN_LIGHT,
            WMO_FREEZING_RAIN_HEAVY,
            -> "Rain"

            WMO_SNOW_SLIGHT,
            WMO_SNOW_MODERATE,
            WMO_SNOW_HEAVY,
            WMO_SNOW_GRAINS,
            -> "Snow"

            WMO_SHOWERS_SLIGHT, WMO_SHOWERS_MODERATE, WMO_SHOWERS_VIOLENT -> "Showers"

            WMO_SNOW_SHOWERS_SLIGHT, WMO_SNOW_SHOWERS_HEAVY -> "Snow showers"

            WMO_THUNDERSTORM, WMO_THUNDER_HAIL_SLIGHT, WMO_THUNDER_HAIL_HEAVY -> "Storm"

            else -> "Weather"
        }
}
