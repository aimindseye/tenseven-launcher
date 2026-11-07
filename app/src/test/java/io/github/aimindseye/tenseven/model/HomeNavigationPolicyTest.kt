package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNavigationPolicyTest {
    @Test fun compactNeedsKeyboardAndBothCallKeys() {
        assertTrue(HomeNavigationPolicy.compact(true, true, true))
        assertFalse(HomeNavigationPolicy.compact(true, false, true))
        assertFalse(HomeNavigationPolicy.compact(true, true, false))
        assertFalse(HomeNavigationPolicy.compact(false, true, true))
    }

    @Test fun defaultShortcutsAdaptWithoutChangingSavedTokens() {
        assertEquals(listOf(QuickBar.PHONE, QuickBar.COMMAND), HomeNavigationPolicy.shortcuts(QuickBar.DEFAULT, false))
        assertEquals(emptyList<String>(), HomeNavigationPolicy.shortcuts(QuickBar.DEFAULT, true))
        assertEquals(listOf(QuickBar.PHONE, QuickBar.COMMAND, QuickBar.CAMERA), QuickBar.DEFAULT)
    }

    @Test fun customAppShortcutsSurviveOnCompactDevices() {
        val saved = listOf(QuickBar.appToken("app-id"), QuickBar.COMMAND, QuickBar.CAMERA)
        assertEquals(saved.take(2), HomeNavigationPolicy.shortcuts(saved, true))
    }
}
