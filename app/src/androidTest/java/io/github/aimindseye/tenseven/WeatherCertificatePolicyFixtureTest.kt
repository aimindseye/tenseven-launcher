package io.github.aimindseye.tenseven

import android.net.http.X509TrustManagerExtensions
import android.os.StrictMode
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** REVIEW ONLY: network-independent installed Android trust/pin tests; no TLS-session/HTTP guarantee. */
class WeatherCertificatePolicyFixtureTest {
    @Test
    fun forecastCertificateFixtureMatchesTheInstalledPolicy() =
        withoutNetwork { assertAcceptedFixture("api.open-meteo.com", "forecast-chain.pem") }

    @Test
    fun geocodingCertificateFixtureMatchesTheInstalledPolicy() =
        withoutNetwork { assertAcceptedFixture("geocoding-api.open-meteo.com", "geocoding-chain.pem") }

    @Test
    fun normallyTrustedUnrelatedCaIsRejectedByTheWeatherPinPolicy() =
        withoutNetwork {
            val trust = installedTrust()
            val chain = fixture("unrelated-chain.pem")
            chain.forEach { it.checkValidity() }
            val authType = chain.first().publicKey.algorithm
            trust.checkServerTrusted(chain, authType, "www.google.com")
            assertFalse(trust.isSameTrustConfiguration("www.google.com", "api.open-meteo.com"))
            val error =
                assertThrows(CertificateException::class.java) {
                    trust.checkServerTrusted(chain, authType, "api.open-meteo.com")
                }
            assertTrue(error.message.orEmpty().contains("pin", ignoreCase = true))
        }

    @Test
    fun invalidLeafSignatureCannotBorrowTheOtherwiseValidPinnedChain() =
        withoutNetwork {
            val trust = installedTrust()
            val original = fixture("forecast-chain.pem")
            val altered = fixture("forecast-invalid-signature.der").single()
            val chain = (listOf(altered) + original.drop(1)).toTypedArray()
            chain.forEach { it.checkValidity() }
            val authType = chain.first().publicKey.algorithm
            assertThrows(CertificateException::class.java) {
                trust.checkServerTrusted(chain, authType, "api.open-meteo.com")
            }
        }

    private fun assertAcceptedFixture(
        host: String,
        name: String,
    ) {
        val trust = installedTrust()
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host))
        val chain = fixture(name)
        chain.forEach { it.checkValidity() }
        assertTrue(trust.checkServerTrusted(chain, chain.first().publicKey.algorithm, host).isNotEmpty())
    }

    private fun installedTrust(): X509TrustManagerExtensions {
        // Uses the target application's platform configuration, not a test KeyStore, trust-all manager or test CA.
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return X509TrustManagerExtensions(factory.trustManagers.filterIsInstance<X509TrustManager>().single())
    }

    private fun fixture(name: String): Array<X509Certificate> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open("weather-certificate-policy/$name").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == expectedCertificateFixtureHashes.getValue(name)) { "Unreviewed certificate fixture" }
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificates(bytes.inputStream()).map { it as X509Certificate }.toTypedArray()
    }

    private fun withoutNetwork(assertions: () -> Unit) {
        val previous = StrictMode.getThreadPolicy()
        val offline =
            StrictMode.ThreadPolicy
                .Builder(previous)
                .detectNetwork()
                .penaltyDeathOnNetwork()
                .build()
        StrictMode.setThreadPolicy(offline)
        try {
            assertions()
        } finally {
            StrictMode.setThreadPolicy(previous)
        }
    }
}

private val expectedCertificateFixtureHashes =
    mapOf(
        "forecast-chain.pem" to "752c85507f3fa4fa19e6f216231597faab16b5f5c76142423dfab2b232d5f85b",
        "geocoding-chain.pem" to "0a4d40bb6f1163f026abaee5398d81267d14d0ba7b3b0a785092c3431a406777",
        "unrelated-chain.pem" to "d81c56d7ab53af24c3d2fa96d95cc4732828eaccdbe7d50c3756cdc0dfbc3237",
        "forecast-invalid-signature.der" to "2a44cea37eb0f57ed77b1cf83c8b1a8279560db43bfa431773d9c1591446186a",
    )
