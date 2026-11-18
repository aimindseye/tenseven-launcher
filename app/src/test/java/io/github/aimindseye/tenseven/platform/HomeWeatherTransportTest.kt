package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeWeatherTransportTest {
    @Test
    fun acceptedRequestRetainsTimeoutsAndClosesConnection() {
        val connection = WeatherConnection()
        val result = HomeWeatherTransport.json(FORECAST) { connection }
        assertEquals(21, result.getInt("temperature"))
        assertEquals("GET", connection.requestMethod)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(5000, connection.connectTimeout)
        assertEquals(5000, connection.readTimeout)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test
    fun unauthorizedDestinationsFailBeforeOpeningConnection() {
        val rejected =
            listOf(
                "http://api.open-meteo.com/v1/forecast",
                "https://api.open-meteo.com.attacker.invalid/",
                "https://attacker.invalid/?api.open-meteo.com",
                "https://api.open-meteo.com@attacker.invalid/",
                "https://user@api.open-meteo.com/",
                "https://api.open-meteo.com:8443/",
                "https://api.open-meteo.com/#redirect",
            )
        var opened = false
        rejected.forEach { address ->
            assertThrows(IllegalArgumentException::class.java) {
                HomeWeatherTransport.json(address) {
                    opened = true
                    WeatherConnection()
                }
            }
        }
        assertFalse(opened)
    }

    @Test
    fun geocodingUsesOnlyItsOriginalHttpsHost() {
        var observed: URL? = null
        HomeWeatherTransport.json("https://geocoding-api.open-meteo.com/v1/search?name=Berlin") {
            observed = it
            WeatherConnection()
        }
        assertEquals("geocoding-api.open-meteo.com", observed?.host)
        assertEquals("name=Berlin", observed?.query)
    }

    @Test
    fun redirectsAndHttpErrorsFailAndDisconnect() {
        listOf(301, 302, 307, 308, 404, 500).forEach { code ->
            val connection = WeatherConnection(code)
            assertThrows(IllegalStateException::class.java) {
                HomeWeatherTransport.json(FORECAST) { connection }
            }
            assertTrue(connection.disconnected)
            assertFalse(connection.bodyRead)
        }
    }

    @Test
    fun oversizedPayloadFailsBeforeUnboundedAllocation() {
        val connection = WeatherConnection(payload = " ".repeat(160_001))
        assertThrows(IllegalStateException::class.java) {
            HomeWeatherTransport.json(FORECAST) { connection }
        }
        assertTrue(connection.disconnected)
    }

    @Test
    fun malformedResponseStillDisconnects() {
        val connection = WeatherConnection(payload = "not json")
        assertThrows(org.json.JSONException::class.java) {
            HomeWeatherTransport.json(FORECAST) { connection }
        }
        assertTrue(connection.disconnected)
    }

    private companion object {
        const val FORECAST = "https://api.open-meteo.com/v1/forecast?latitude=0&longitude=0"
    }
}

private class WeatherConnection(
    private val code: Int = 200,
    private val payload: String = "{\"temperature\":21}",
) : HttpsURLConnection(URL("https://api.open-meteo.com/")) {
    var disconnected = false
    var bodyRead = false

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy() = false

    override fun connect() = Unit

    override fun getResponseCode() = code

    override fun getInputStream(): ByteArrayInputStream {
        bodyRead = true
        return ByteArrayInputStream(payload.toByteArray())
    }

    override fun getCipherSuite() = "TLS_AES_128_GCM_SHA256"

    override fun getLocalCertificates(): Array<Certificate>? = null

    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}
