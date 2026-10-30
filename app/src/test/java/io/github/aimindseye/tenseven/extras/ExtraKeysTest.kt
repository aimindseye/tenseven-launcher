package io.github.aimindseye.tenseven.extras

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtraKeysTest {
    @Test
    fun escapeLeavesAndMenuShowsTheKeys() {
        assertEquals(ExtraKeyAction.Leave, ExtraKeys.action(KeyEvent.KEYCODE_ESCAPE, 0))
        assertEquals(ExtraKeyAction.Help, ExtraKeys.action(KeyEvent.KEYCODE_MENU, 0))
    }

    @Test
    fun heldKeysChordsAndOtherKeysAreLeftAlone() {
        assertNull(ExtraKeys.action(KeyEvent.KEYCODE_ESCAPE, 1))
        assertNull(ExtraKeys.action(KeyEvent.KEYCODE_MENU, 0, alt = true))
        assertNull(ExtraKeys.action(KeyEvent.KEYCODE_ESCAPE, 0, ctrl = true))
        assertNull(ExtraKeys.action(KeyEvent.KEYCODE_A, 0))
    }
}
