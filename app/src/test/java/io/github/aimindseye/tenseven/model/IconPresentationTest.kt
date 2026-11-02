package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPresentationTest {
    @Test fun defaultIsOriginalAndUnknownChoiceDoesNotForceTablerIcons() {
        assertEquals(IconPresentation.Original, IconPresentation.decode(null))
        assertEquals(IconPresentation.Original, IconPresentation.decode("unknown"))
        assertEquals(IconPresentation.TenSeven, IconPresentation.decode("tenseven"))
    }

    @Test fun externalIconPackAndOriginalArtworkTakePriority() {
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.Original, false, false))
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, false, true))
        assertTrue(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, false, false))
        assertFalse(IconPresentationPolicy.useMonochrome(IconPresentation.Original, false))
        assertFalse(IconPresentationPolicy.useMonochrome(IconPresentation.TenSeven, true))
    }

    @Test fun bundledTenSevenAppKeepsItsOwnGlyph() {
        assertTrue(IconPresentationPolicy.useGlyph(IconPresentation.Original, true, false))
        assertTrue(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, true, true))
    }
}
