package io.github.aimindseye.tenseven.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SableViewPaletteTest {
    @Test fun accentIsSableBlueInLightAndDark() {
        assertEquals(0xFF2E9BD6.toInt(), SableViewPalette.of(night = true).accent)
        assertEquals(0xFF2E9BD6.toInt(), SableViewPalette.of(night = false).accent)
    }

    @Test fun lightAndDarkDiffer() {
        assertNotEquals(SableViewPalette.of(true).bg, SableViewPalette.of(false).bg)
        assertNotEquals(SableViewPalette.of(true).fg, SableViewPalette.of(false).fg)
    }
}
