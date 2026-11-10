package io.github.aimindseye.tenseven.model

/** Bounded launcher-owned typography; never changes Android's system font size. */
enum class LauncherTextSize(
    val multiplier: Float,
) {
    Small(0.90f),
    Default(1.0f),
    Large(1.10f),
    ;

    companion object {
        fun decode(value: String?): LauncherTextSize = entries.firstOrNull { it.name == value } ?: Default
    }
}

/** Compact WMO descriptions to avoid wrapping status/date header text. */
object HomeWeatherCodes {
    fun description(code: Int): String =
        when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Storm"
            else -> "Weather"
        }
}
