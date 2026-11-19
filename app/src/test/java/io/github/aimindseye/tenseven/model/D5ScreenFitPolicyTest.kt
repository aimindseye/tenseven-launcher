package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class D5ScreenFitPolicyTest {
    @Test fun titan2UsesTwoDialSuggestions() {
        val fit = D5ScreenFitPolicy.forViewport(576f, 576f)
        assertFalse(fit.compact)
        assertEquals(2, fit.dialMatchRows)
        assertEquals(54, fit.dialCallDp)
    }

    @Test fun q25KeepsCallActionVisible() {
        val fit = D5ScreenFitPolicy.forViewport(411f, 411f)
        assertTrue(fit.compact)
        assertEquals(1, fit.dialMatchRows)
        assertEquals(48, fit.dialCallDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidViewportIsRejected() {
        D5ScreenFitPolicy.forViewport(0f, 576f)
    }
}
