package io.github.aimindseye.tenseven.platform

import io.github.aimindseye.tenseven.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherTlsConfigurationTest {
    @Test
    fun bothWeatherHostsHaveReviewedRootPinsWithoutExpiryOrDebugOverrides() {
        val resources = RuntimeEnvironment.getApplication().resources
        val domains = mutableListOf<String>()
        val pins = mutableListOf<String>()
        resources.getXml(R.xml.weather_network_security_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "domain" -> {
                        assertEquals("false", parser.getAttributeValue(null, "includeSubdomains"))
                        domains.add(parser.nextText())
                    }

                    "pin" -> {
                        assertEquals("SHA-256", parser.getAttributeValue(null, "digest"))
                        pins.add(parser.nextText())
                    }

                    "pin-set" -> {
                        assertNull(parser.getAttributeValue(null, "expiration"))
                    }

                    "certificates" -> {
                        assertEquals("system", parser.getAttributeValue(null, "src"))
                    }

                    "base-config", "domain-config" -> {
                        assertEquals("false", parser.getAttributeValue(null, "cleartextTrafficPermitted"))
                    }

                    else -> {
                        assertFalse(parser.name == "debug-overrides")
                    }
                }
            }
        }
        assertEquals(listOf("api.open-meteo.com", "geocoding-api.open-meteo.com"), domains)
        assertEquals(
            listOf("C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", "diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="),
            pins,
        )
    }
}
