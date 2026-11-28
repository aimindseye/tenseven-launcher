package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPresentationTest {
    @Test fun defaultIsTenSevenAndExplicitOriginalIsRespected() {
        assertEquals(IconPresentation.TenSeven, IconPresentation.decode(null))
        assertEquals(IconPresentation.TenSeven, IconPresentation.decode("unknown"))
        assertEquals(IconPresentation.Original, IconPresentation.decode("original"))
        assertEquals(IconPresentation.TenSeven, IconPresentation.decode("tenseven"))
    }

    @Test fun externalIconPackAndOriginalArtworkTakePriority() {
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.Original, false, false))
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, false, true))
        assertTrue(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, false, false))
        assertFalse(IconPresentationPolicy.useMonochrome(IconPresentation.Original, false))
        assertFalse(IconPresentationPolicy.useMonochrome(IconPresentation.TenSeven, true))
    }

    @Test fun bundledAppsAlsoRespectOriginalAndExternalPack() {
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.Original, true, false))
        assertFalse(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, true, true))
        assertTrue(IconPresentationPolicy.useGlyph(IconPresentation.TenSeven, true, false))
    }
}
