package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    private fun detect(
        model: String,
        manufacturer: String = "",
        device: String = "",
        qwerty: Boolean = false,
        arrows: Boolean = false,
    ) = DeviceProfile.detect(manufacturer, model, device, qwerty, arrows)

    @Test fun zinwaPhonesMoveWithArrows() {
        assertEquals(DeviceProfile.Q25, detect("Q25", "Zinwa"))
        assertEquals(DeviceProfile.Q25, detect("Sable Q25"))
        assertEquals(DeviceProfile.Q27, detect("Q27", "Zinwa"))
        assertFalse(DeviceProfile.Q25.letterMoves)
        assertFalse(DeviceProfile.Q27.letterMoves)
    }

    @Test fun titanPhonesMoveWithLetters() {
        assertEquals(DeviceProfile.TITAN_2, detect("Titan 2", "Unihertz"))
        assertEquals(DeviceProfile.TITAN_2, detect("TITAN2"))
        assertEquals(DeviceProfile.TITAN_2, detect("Titan_2"))
        assertEquals(DeviceProfile.TITAN_2_ELITE, detect("Titan 2 Elite", "Unihertz"))
        assertEquals(DeviceProfile.TITAN_2_ELITE, detect("", device = "titan2elite"))
        assertTrue(DeviceProfile.TITAN_2.letterMoves)
        assertTrue(DeviceProfile.TITAN_2_ELITE.letterMoves)
    }

    @Test fun longerModelNumbersAreNotMistaken() {
        assertEquals(DeviceProfile.OTHER, detect("SM-Q250", "Samsung"))
        assertEquals(DeviceProfile.OTHER, detect("Q270X"))
        assertEquals(DeviceProfile.OTHER, detect("Titan 20"))
    }

    @Test fun passportAndUnknownKeyboardPhones() {
        assertEquals(DeviceProfile.P26, detect("P26", "Zinwa"))
        assertEquals(DeviceProfile.P26, detect("BlackBerry Passport"))
        assertEquals(DeviceProfile.KEYBOARD_NO_ARROWS, detect("Other", qwerty = true, arrows = false))
        assertEquals(DeviceProfile.OTHER, detect("Other", qwerty = true, arrows = true))
        assertEquals(DeviceProfile.OTHER, detect("Pixel 9"))
    }

    @Test fun onlyTheQ25KeepsTheCompactHomeBar() {
        assertEquals(listOf(DeviceProfile.Q25), DeviceProfile.entries.filter { it.compactHomeBar })
    }

    @Test fun unconfirmedProfilesSaySo() {
        assertTrue(DeviceProfile.Q27.unverified)
        assertTrue(DeviceProfile.TITAN_2_ELITE.unverified)
        assertFalse(DeviceProfile.Q25.unverified)
        assertFalse(DeviceProfile.TITAN_2.unverified)
    }
}
