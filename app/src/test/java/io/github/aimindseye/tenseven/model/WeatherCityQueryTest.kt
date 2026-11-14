package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCityQueryTest {
    @Test fun preservesAdminAreaCountryAndInternationalNames() {
        assertEquals("Paris, France", WeatherCityQuery.normalized("  Paris,  France  "))
        assertEquals("New York, NY", WeatherCityQuery.normalized("New York, NY"))
        assertEquals("São Paulo, Brazil", WeatherCityQuery.normalized("São Paulo, Brazil"))
    }

    @Test fun neverInfersDeviceLocation() {
        assertEquals("", WeatherCityQuery.normalized("    "))
        assertEquals("Jersey City", WeatherCityQuery.normalized("Jersey City"))
    }
}
