package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.edit
import io.github.aimindseye.tenseven.model.HomeWeatherCodes
import io.github.aimindseye.tenseven.model.WeatherCityQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val WEATHER_HTTP_SUCCESS_MAX = 299

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val WEATHER_CURRENT_NEAR_DEVIC_TIME_1000_L = 1000L
private const val WEATHER_CURRENT_NEAR_DEVIC_TIME_8_000_L = 8_000L
private const val WEATHER_CURRENT_NEAR_DEVIC_VALUE_2 = 2
private const val WEATHER_CURRENT_NEAR_DEVIC_VALUE_60 = 60
private const val WEATHER_JSON_MAX_160_000 = 160_000
private const val WEATHER_JSON_VALUE_200 = 200
private const val WEATHER_JSON_VALUE_5000 = 5000
private const val WEATHER_RESOLVE_LOCALITY_TIME_3_500_L = 3_500L

/** The user chooses a city. No GPS permission, IP-location lookup or tracking is used. */
internal data class HomeWeatherObservation(
    val temperature: Int,
    val unit: String,
    val condition: String,
    val locationName: String? = null,
)

internal class HomeWeatherClient(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private var nearbyAt = 0L
    private var nearbyWeather: HomeWeatherObservation? = null
    private val cache =
        context.applicationContext.getSharedPreferences(
            "tenseven_home_weather_cache",
            Context.MODE_PRIVATE,
        )

    suspend fun current(
        city: String,
        fahrenheit: Boolean,
    ): HomeWeatherObservation? =
        withContext(Dispatchers.IO) {
            val normalized = WeatherCityQuery.normalized(city)
            if (normalized.length < 2) return@withContext null
            val cacheKey = normalized.lowercase(Locale.ROOT) + if (fahrenheit) "|f" else "|c"
            val savedAt = cache.getLong("timestamp", 0)
            val cached =
                if (cache.getString("city_unit", null) == cacheKey) {
                    cache.getString("weather", null)?.let { decode(it) }
                } else {
                    null
                }
            val now = System.currentTimeMillis()
            if (cached != null && now - savedAt in 0 until CACHE_LIFETIME_MS) return@withContext cached
            runCatching { fetchCityObservation(normalized, fahrenheit, cacheKey, now) }.getOrNull()
                ?: cached?.takeIf { now - savedAt in 0 until MAX_STALE_MS }
        }

    private fun fetchCityObservation(
        normalized: String,
        fahrenheit: Boolean,
        cacheKey: String,
        now: Long,
    ): HomeWeatherObservation? {
        // Open-Meteo's geocoder accepts a city name; do not transmit personal device location.
        val coordinates = resolveCityCoordinates(normalized) ?: return null
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val data =
            json(
                "https://api.open-meteo.com/v1/forecast?latitude=${coordinates.first}&longitude=${coordinates.second}" +
                    "&current=temperature_2m,weather_code&temperature_unit=$unit&timezone=auto",
            )
        return saveCityObservation(data.optJSONObject("current"), fahrenheit, cacheKey, now)
    }

    private fun resolveCityCoordinates(normalized: String): Pair<Double, Double>? {
        val city = resolveWeatherCity(normalized) ?: return null
        val latitude = city.optDouble("latitude", Double.NaN)
        val longitude = city.optDouble("longitude", Double.NaN)
        return if (latitude.isFinite() && longitude.isFinite()) latitude to longitude else null
    }

    private fun saveCityObservation(
        current: JSONObject?,
        fahrenheit: Boolean,
        cacheKey: String,
        now: Long,
    ): HomeWeatherObservation? {
        val temp = current?.optDouble("temperature_2m", Double.NaN) ?: Double.NaN
        if (!temp.isFinite()) return null
        val weatherCode = current?.optInt("weather_code", -1) ?: -1
        val observation =
            HomeWeatherObservation(
                temp.roundToInt(),
                if (fahrenheit) "°F" else "°C",
                HomeWeatherCodes.description(weatherCode),
            )
        cache.edit {
            putString("city_unit", cacheKey)
            putLong("timestamp", now)
            putString("weather", "${observation.temperature}|$weatherCode")
        }
        return observation
    }

    /** No raw device coordinates are persisted. Approximate location is consulted only on Home. */
    @android.annotation.SuppressLint("MissingPermission") // guarded by coarse location check at start of method
    suspend fun currentNearDevice(fahrenheit: Boolean): HomeWeatherObservation? =
        withContext(Dispatchers.IO) {
            if (applicationContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager
                    .PERMISSION_GRANTED
            ) {
                return@withContext null
            }
            val now = System.currentTimeMillis()
            val cached = nearbyWeather
            if (cached != null && now - nearbyAt in 0 until CACHE_LIFETIME_MS) return@withContext cached
            val manager = applicationContext.getSystemService(LocationManager::class.java) ?: return@withContext null
            val provider = nearbyProvider(manager) ?: return@withContext null
            val location = recentOrCurrentLocation(manager, provider, now) ?: return@withContext null
            val observation = nearbyForecast(location, fahrenheit)
            if (observation != null) {
                nearbyWeather = observation
                nearbyAt = now
            }
            observation ?: cached?.takeIf { now - nearbyAt in 0 until MAX_STALE_MS }
        }

    private fun resolveWeatherCity(normalized: String): JSONObject? {
        val query = Uri.encode(normalized.substringBefore(",").trim())
        val matches =
            json("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=10&language=en&format=json")
                .optJSONArray("results")
        val requestedRegion = normalized.substringAfter(',', "").trim()
        val candidates =
            if (matches == null) {
                emptyList()
            } else {
                (0 until matches.length()).mapNotNull { matches.optJSONObject(it) }
            }
        return candidates.firstOrNull {
            requestedRegion.isNotEmpty() && (
                it.optString("admin1").equals(requestedRegion, ignoreCase = true) ||
                    it.optString("country_code").equals(requestedRegion, ignoreCase = true)
            )
        } ?: candidates.firstOrNull()
    }

    private fun nearbyProvider(manager: LocationManager): String? =
        when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> null
        }

    private suspend fun nearbyForecast(
        location: Location,
        fahrenheit: Boolean,
    ): HomeWeatherObservation? {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&temperature_unit=" +
                (if (fahrenheit) "fahrenheit" else "celsius") + "&timezone=auto"
        return runCatching {
            val current = json(url).optJSONObject("current") ?: return@runCatching null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (!temp.isFinite()) return@runCatching null
            HomeWeatherObservation(
                temp.roundToInt(),
                if (fahrenheit) "°F" else "°C",
                HomeWeatherCodes.description(current.optInt("weather_code", -1)),
                locationName = resolveLocality(location) ?: "Approximate location",
            )
        }.getOrNull()
    }

    // Called only after currentNearDevice has verified ACCESS_COARSE_LOCATION.
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun recentOrCurrentLocation(
        manager: LocationManager,
        provider: String,
        now: Long,
    ): Location? {
        val recent =
            runCatching { manager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.takeIf {
                    now - it.time in
                        0..(
                            WEATHER_CURRENT_NEAR_DEVIC_VALUE_2 * WEATHER_CURRENT_NEAR_DEVIC_VALUE_60 *
                                WEATHER_CURRENT_NEAR_DEVIC_VALUE_60 *
                                WEATHER_CURRENT_NEAR_DEVIC_TIME_1000_L
                        )
                }
        return recent ?: if (provider == LocationManager.NETWORK_PROVIDER) {
            withTimeoutOrNull(WEATHER_CURRENT_NEAR_DEVIC_TIME_8_000_L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    runCatching {
                        manager.getCurrentLocation(provider, null, applicationContext.mainExecutor) { result ->
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }.onFailure { if (continuation.isActive) continuation.resume(null) }
                }
            }
        } else {
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
    }

    /** Android's coarse location only; a city label is best-effort, not guessed. */
    private suspend fun resolveLocality(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(WEATHER_RESOLVE_LOCALITY_TIME_3_500_L) {
            suspendCancellableCoroutine<String?> { continuation ->
                val geocoder = Geocoder(applicationContext, Locale.getDefault())
                runCatching {
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val place =
                                    addresses.firstOrNull()?.let { address ->
                                        address.locality?.takeIf { it.isNotBlank() }
                                            ?: address.subAdminArea?.takeIf { it.isNotBlank() }
                                            ?: address.adminArea?.takeIf { it.isNotBlank() }
                                    }
                                if (continuation.isActive) continuation.resume(place)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    private fun decode(text: String): HomeWeatherObservation? {
        val pieces = text.split('|')
        val temperature = pieces.getOrNull(0)?.toIntOrNull()
        val code = pieces.getOrNull(1)?.toIntOrNull()
        return if (pieces.size != 2 || temperature == null || code == null) {
            null
        } else {
            val fahrenheit = cache.getString("city_unit", "")?.endsWith("|f") == true
            HomeWeatherObservation(temperature, if (fahrenheit) "°F" else "°C", HomeWeatherCodes.description(code))
        }
    }

    private fun json(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = WEATHER_JSON_VALUE_5000
            connection.readTimeout = WEATHER_JSON_VALUE_5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in WEATHER_JSON_VALUE_200..WEATHER_HTTP_SUCCESS_MAX) {
                error("Weather HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText().take(WEATHER_JSON_MAX_160_000) }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CACHE_LIFETIME_MS = 30 * 60_000L
        private const val MAX_STALE_MS = 90 * 60_000L
    }
}
