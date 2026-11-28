package io.github.aimindseye.tenseven.design

import android.view.KeyEvent
import io.github.aimindseye.tenseven.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterMoveKeysTest {
    @Test fun lettersMoveOnPhonesWithoutArrows() {
        val titan = DeviceProfile.TITAN_2
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_I, 0, false))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_J, 0, false))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_K, 0, false))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_L, 0, false))
    }

    @Test fun phonesWithArrowsKeepTheirLetters() {
        assertNull(LetterMoveKeys.remap(DeviceProfile.Q25, KeyEvent.KEYCODE_I, 0, false))
        assertNull(LetterMoveKeys.remap(DeviceProfile.OTHER, KeyEvent.KEYCODE_L, 0, false))
    }

    @Test fun typingAndModifiersKeepTheLetter() {
        val titan = DeviceProfile.TITAN_2
        assertNull(LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_I, 0, true))
        assertNull(LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_I, KeyEvent.META_ALT_ON, false))
        assertNull(LetterMoveKeys.remap(titan, KeyEvent.KEYCODE_A, 0, false))
    }
}
