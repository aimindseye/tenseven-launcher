package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.Geocoder
import android.location.Address
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import android.net.Uri
import io.github.aimindseye.tenseven.model.HomeWeatherCodes
import io.github.aimindseye.tenseven.model.WeatherCityQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** The user chooses a city. No GPS permission, IP-location lookup or tracking is used. */
internal data class HomeWeatherObservation(val temperature: Int, val unit: String, val condition: String, val locationName: String? = null)

internal class HomeWeatherClient(context: Context) {
    private val applicationContext = context.applicationContext
    private var nearbyAt = 0L
    private var nearbyWeather: HomeWeatherObservation? = null
    private val cache = context.applicationContext.getSharedPreferences("tenseven_home_weather_cache", Context.MODE_PRIVATE)

    suspend fun current(city: String, fahrenheit: Boolean): HomeWeatherObservation? = withContext(Dispatchers.IO) {
        val normalized = WeatherCityQuery.normalized(city)
        if (normalized.length < 2) return@withContext null
        val cacheKey = normalized.lowercase(Locale.ROOT) + if (fahrenheit) "|f" else "|c"
        val savedAt = cache.getLong("timestamp", 0)
        val cached = if (cache.getString("city_unit", null) == cacheKey) {
            cache.getString("weather", null)?.let { decode(it) }
        } else null
        val now = System.currentTimeMillis()
        if (cached != null && now - savedAt in 0 until CACHE_LIFETIME_MS) return@withContext cached
        runCatching {
            // Open-Meteo's geocoder accepts a city name; do not transmit personal device location.
            val query = Uri.encode(normalized.substringBefore(",").trim())
            val matches = json("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=10&language=en&format=json")
                .optJSONArray("results") ?: return@runCatching null
            if (matches.length() == 0) return@runCatching null
            val requestedRegion = normalized.substringAfter(',', "").trim()
            val cityMatch = (0 until matches.length()).mapNotNull { matches.optJSONObject(it) }
                .firstOrNull { requestedRegion.isNotEmpty() && (
                    it.optString("admin1").equals(requestedRegion, ignoreCase = true) ||
                        it.optString("country_code").equals(requestedRegion, ignoreCase = true)
                    ) } ?: matches.optJSONObject(0) ?: return@runCatching null
            val latitude = cityMatch.optDouble("latitude", Double.NaN)
            val longitude = cityMatch.optDouble("longitude", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite()) return@runCatching null
            val unit = if (fahrenheit) "fahrenheit" else "celsius"
            val data = json("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code&temperature_unit=$unit&timezone=auto")
            val current = data.optJSONObject("current") ?: return@runCatching null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (!temp.isFinite()) return@runCatching null
            val observation = HomeWeatherObservation(
                temp.roundToInt(), if (fahrenheit) "°F" else "°C",
                HomeWeatherCodes.description(current.optInt("weather_code", -1)),
            )
            cache.edit().putString("city_unit", cacheKey)
                .putLong("timestamp", now)
                .putString("weather", "${observation.temperature}|${current.optInt("weather_code", -1)}")
                .apply()
            observation
        }.getOrNull() ?: cached?.takeIf { now - savedAt in 0 until MAX_STALE_MS }
    }

    /** No raw device coordinates are persisted. Approximate location is consulted only on Home. */
    @android.annotation.SuppressLint("MissingPermission") // guarded by coarse location check at start of method
    suspend fun currentNearDevice(fahrenheit: Boolean): HomeWeatherObservation? = withContext(Dispatchers.IO) {
        if (applicationContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        val now = System.currentTimeMillis()
        val cached = nearbyWeather
        if (cached != null && now - nearbyAt in 0 until CACHE_LIFETIME_MS) return@withContext cached
        val manager = applicationContext.getSystemService(LocationManager::class.java) ?: return@withContext null
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> return@withContext null
        }
        val recent = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            ?.takeIf { now - it.time in 0..(2 * 60 * 60 * 1000L) }
        val location = recent ?: if (provider == LocationManager.NETWORK_PROVIDER) {
            withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    runCatching {
                        manager.getCurrentLocation(provider, null, applicationContext.mainExecutor) { result ->
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }.onFailure { if (continuation.isActive) continuation.resume(null) }
                }
            }
        } else runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        location ?: return@withContext null
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code&temperature_unit=${if (fahrenheit) "fahrenheit" else "celsius"}&timezone=auto"
        val observation = runCatching {
            val current = json(url).optJSONObject("current") ?: return@runCatching null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (!temp.isFinite()) return@runCatching null
            HomeWeatherObservation(temp.roundToInt(), if (fahrenheit) "°F" else "°C",
                HomeWeatherCodes.description(current.optInt("weather_code", -1)),
                locationName = resolveLocality(location) ?: "Approximate location")
        }.getOrNull()
        if (observation != null) { nearbyWeather = observation; nearbyAt = now }
        observation ?: cached?.takeIf { now - nearbyAt in 0 until MAX_STALE_MS }
    }

    /** Android's coarse location only; a city label is best-effort, not guessed. */
    private suspend fun resolveLocality(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(3_500L) {
            suspendCancellableCoroutine<String?> { continuation ->
                val geocoder = Geocoder(applicationContext, Locale.getDefault())
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val place = addresses.firstOrNull()?.let { address ->
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
        if (pieces.size != 2) return null
        val temperature = pieces[0].toIntOrNull() ?: return null
        val code = pieces[1].toIntOrNull() ?: return null
        val fahrenheit = cache.getString("city_unit", "")?.endsWith("|f") == true
        return HomeWeatherObservation(temperature, if (fahrenheit) "°F" else "°C", HomeWeatherCodes.description(code))
    }

    private fun json(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("Weather HTTP ${connection.responseCode}")
            val response = connection.inputStream.bufferedReader().use { it.readText().take(160_000) }
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
