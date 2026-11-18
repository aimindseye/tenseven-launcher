package io.github.aimindseye.tenseven

import android.net.http.X509TrustManagerExtensions
import android.security.NetworkSecurityPolicy
import io.github.aimindseye.tenseven.platform.HomeWeatherTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Real Android trust/pin enforcement, not JVM mocks. Only public synthetic weather coordinates are used. */
class WeatherTlsInstrumentationTest {
    @Test
    fun currentWeatherAndGeocodingChainsPassTheInstalledPinPolicy() {
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("api.open-meteo.com"))
        val weather =
            HomeWeatherTransport.json(
                "https://api.open-meteo.com/v1/forecast?latitude=0&longitude=0&current=temperature_2m,weather_code",
            )
        assertTrue(weather.getJSONObject("current").has("temperature_2m"))
        val cities = HomeWeatherTransport.json("https://geocoding-api.open-meteo.com/v1/search?name=Berlin&count=1")
        assertTrue(cities.getJSONArray("results").length() > 0)
    }

    @Test
    fun unrelatedNormallyTrustedCaCannotAuthenticateAWeatherHost() {
        val chain = unrelatedTrustedChain()
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val trust = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val extensions = X509TrustManagerExtensions(trust)
        val authType = chain.first().publicKey.algorithm
        extensions.checkServerTrusted(chain, authType, "www.google.com")
        assertFalse(extensions.isSameTrustConfiguration("www.google.com", "api.open-meteo.com"))
        val rejected =
            assertThrows(CertificateException::class.java) {
                extensions.checkServerTrusted(chain, authType, "api.open-meteo.com")
            }
        assertTrue("Expected pin rejection, got $rejected", rejected.message.orEmpty().contains("pin", ignoreCase = true))
    }

    private fun unrelatedTrustedChain(): Array<X509Certificate> {
        val connection = URL("https://www.google.com/generate_204").openConnection() as HttpsURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        return try {
            connection.connect()
            connection.serverCertificates.map { it as X509Certificate }.toTypedArray()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 5000
    }
}
