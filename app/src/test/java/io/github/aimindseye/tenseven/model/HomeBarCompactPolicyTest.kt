package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBarCompactPolicyTest {
    @Test fun q25UsesFourPrimaryDestinations() {
        assertTrue(HomeBarCompactPolicy.compact("Q25", 411))
        assertTrue(HomeBarCompactPolicy.compact("Sable Q25", 450))
        assertTrue(HomeBarCompactPolicy.optionalTokens(QuickBar.DEFAULT, true).isEmpty())
    }

    @Test fun titan2RetainsTwoOptionalShortcuts() {
        assertFalse(HomeBarCompactPolicy.compact("Titan 2", 360))
        assertEquals(listOf(QuickBar.PHONE, QuickBar.COMMAND), HomeBarCompactPolicy.optionalTokens(QuickBar.DEFAULT, false))
    }

    @Test fun customShortcutsAreNotErasedOrDuplicated() {
        val saved = listOf("app:mine", QuickBar.CAMERA, "app:mine", QuickBar.COMMAND)
        assertEquals(listOf("app:mine", QuickBar.COMMAND), HomeBarCompactPolicy.optionalTokens(saved, false))
        assertEquals(4, saved.size)
    }
}
