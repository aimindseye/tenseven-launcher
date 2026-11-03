package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeDisplayPolicyTest {
    @Test fun themeKeysDoNotOverlap() {
        assertNotEquals(HomeDisplayPolicy.dialPreference(HomeStyle.Bb7), HomeDisplayPolicy.dialPreference(HomeStyle.Bb10))
        assertNotEquals(HomeDisplayPolicy.stripPreference(HomeStyle.Bb7), HomeDisplayPolicy.stripPreference(HomeStyle.Bb10))
        assertNotEquals(HomeDisplayPolicy.stripExpandedPreference(HomeStyle.Bb7),
            HomeDisplayPolicy.stripExpandedPreference(HomeStyle.Bb10))
    }
    @Test fun invalidDialIdsAreDropped() {
        assertEquals(setOf("cpu", "battery"),
            HomeDisplayPolicy.normalizedDials(setOf("cpu", "battery", "unknown")))
        assertEquals(emptySet<String>(), HomeDisplayPolicy.normalizedDials(emptySet()))
        assertEquals(7, HomeDisplayPolicy.ALL_DIALS.size)
        assertEquals(6, HomeDisplayPolicy.DEFAULT_DIALS.size)
        assertEquals(setOf("storage"), HomeDisplayPolicy.ALL_DIALS - HomeDisplayPolicy.DEFAULT_DIALS)
    }
}
