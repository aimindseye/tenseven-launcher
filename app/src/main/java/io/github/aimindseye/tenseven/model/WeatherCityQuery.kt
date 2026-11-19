package io.github.aimindseye.tenseven.model

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val WEATHER_CITY_QUERY_NORMALIZED_MAX_90 = 90

/** Pure city normalization. Preserve qualifiers (e.g. Paris, France) for Open-Meteo geocoding. */
internal object WeatherCityQuery {
    fun normalized(value: String): String =
        value
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(WEATHER_CITY_QUERY_NORMALIZED_MAX_90)
}
