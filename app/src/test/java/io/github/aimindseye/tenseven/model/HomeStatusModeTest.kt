package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeStatusModeTest {
    @Test fun newAndMigratedSettings() {
        assertEquals(HomeStatusMode.Dials, HomeStatusMode.decode(null, 3))
        assertEquals(HomeStatusMode.Hidden, HomeStatusMode.decode(null, 0))
        assertEquals(HomeStatusMode.Text, HomeStatusMode.decode("text", 0))
        assertEquals(HomeStatusMode.Dials, HomeStatusMode.decode("dials", 0))
    }

    @Test fun twoStylesHaveIndependentPreferenceKeys() {
        assertNotEquals(HomeStatusMode.preference(HomeStyle.Bb7), HomeStatusMode.preference(HomeStyle.Bb10))
    }
}
