package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RingerProfileTest {
    @Test
    fun cyclesSoundVibrateSilentWhenAllowed() {
        assertEquals(RingerProfile.Vibrate, RingerProfile.Sound.next(canSilence = true))
        assertEquals(RingerProfile.Silent, RingerProfile.Vibrate.next(canSilence = true))
        assertEquals(RingerProfile.Sound, RingerProfile.Silent.next(canSilence = true))
    }

    @Test
    fun withoutDoNotDisturbAccessItNeverPicksSilent() {
        assertEquals(RingerProfile.Vibrate, RingerProfile.Sound.next(canSilence = false))
        assertEquals(RingerProfile.Sound, RingerProfile.Vibrate.next(canSilence = false))
    }

    @Test
    fun ringerModesMapBothWays() {
        RingerProfile.entries.forEach { assertEquals(it, RingerProfile.fromRingerMode(it.ringerMode)) }
        assertEquals(RingerProfile.Sound, RingerProfile.fromRingerMode(42))
    }
}
