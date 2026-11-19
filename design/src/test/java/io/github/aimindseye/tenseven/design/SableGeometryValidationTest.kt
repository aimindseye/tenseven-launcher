package io.github.aimindseye.tenseven.design

import org.junit.Assert.assertTrue
import org.junit.Test

class SableGeometryValidationTest {
    @Test
    fun invalidTouchTargetAndFocusStrokeAreDetected() {
        val violations = SableTokenRules.violations(rowMinimumDp = 47, focusStrokeDp = 1)
        assertTrue("row minimum below 48dp" in violations)
        assertTrue("focus stroke below 2dp" in violations)
    }

    @Test
    fun approvedGeometryAndLargerAccessibleSizesPass() {
        assertTrue(SableTokenRules.violations().isEmpty())
        assertTrue(SableTokenRules.violations(rowMinimumDp = 60, focusStrokeDp = 4).isEmpty())
    }
}
