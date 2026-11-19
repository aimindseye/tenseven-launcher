package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusSizeTest {
    @Test fun defaultIsPreviouslyLarge() {
        assertEquals(HomeStatusSize.Default, HomeStatusSize.decode(null))
        assertEquals(1.10f, HomeStatusSize.Default.multiplier, 0.0001f)
    }

    @Test fun largerExceedsDefaultWithoutFreeSlider() {
        assertEquals(HomeStatusSize.Larger, HomeStatusSize.decode("larger"))
        assertTrue(HomeStatusSize.Larger.multiplier > HomeStatusSize.Default.multiplier)
        assertEquals(HomeStatusSize.Default, HomeStatusSize.decode("invalid"))
    }
}
