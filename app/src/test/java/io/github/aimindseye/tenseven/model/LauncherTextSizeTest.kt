package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTextSizeTest {
    @Test fun boundedPresetsAndFallback() {
        assertEquals(LauncherTextSize.Default, LauncherTextSize.decode("unexpected"))
        assertEquals(LauncherTextSize.Large, LauncherTextSize.decode("Large"))
        assertTrue(LauncherTextSize.Small.multiplier >= .85f)
        assertTrue(LauncherTextSize.Large.multiplier <= 1.12f)
    }
    @Test fun weatherCodeConditions() {
        assertEquals("Clear", HomeWeatherCodes.description(0))
        assertEquals("Rain", HomeWeatherCodes.description(61))
        assertEquals("Weather", HomeWeatherCodes.description(999))
    }
}
