package io.github.aimindseye.tenseven.model

/** Pure city normalization. Preserve qualifiers (e.g. Paris, France) for Open-Meteo geocoding. */
internal object WeatherCityQuery {
    fun normalized(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(90)
}
