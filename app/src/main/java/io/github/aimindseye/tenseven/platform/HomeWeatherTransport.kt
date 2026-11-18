package io.github.aimindseye.tenseven.platform

import org.json.JSONObject
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val WEATHER_HTTPS_PORT = 443
private const val WEATHER_TIMEOUT_MS = 5000
private const val WEATHER_RESPONSE_LIMIT = 160_000
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299

/** Only the weather service endpoints may receive a request; Android owns TLS and pin enforcement. */
internal object HomeWeatherTransport {
    fun json(
        address: String,
        open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    ): JSONObject {
        val url = URL(address)
        require(
            url.protocol == "https" && url.host in setOf("api.open-meteo.com", "geocoding-api.open-meteo.com") &&
                url.port in setOf(-1, WEATHER_HTTPS_PORT) && url.userInfo == null && url.ref == null,
        ) { "Unsupported weather endpoint" }
        val connection = open(url)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = WEATHER_TIMEOUT_MS
            connection.readTimeout = WEATHER_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                "Weather HTTP ${connection.responseCode}"
            }
            return JSONObject(readResponse(connection.inputStream))
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(input: InputStream): String {
        val chars = CharArray(WEATHER_RESPONSE_LIMIT + 1)
        var used = 0
        input.bufferedReader().use { reader ->
            while (used < chars.size) {
                val count = reader.read(chars, used, chars.size - used)
                if (count < 0) break
                check(count > 0) { "Weather response made no progress" }
                used += count
            }
        }
        check(used <= WEATHER_RESPONSE_LIMIT) { "Weather response too large" }
        return String(chars, 0, used)
    }
}
