package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundProfileTest {
    @Test
    fun cyclesSoundVibrateSilentWhenAllowed() {
        assertEquals(SoundProfile.Vibrate, SoundProfile.Sound.next(canSilence = true))
        assertEquals(SoundProfile.Silent, SoundProfile.Vibrate.next(canSilence = true))
        assertEquals(SoundProfile.Sound, SoundProfile.Silent.next(canSilence = true))
    }

    @Test
    fun withoutDoNotDisturbAccessItNeverPicksSilent() {
        assertEquals(SoundProfile.Vibrate, SoundProfile.Sound.next(canSilence = false))
        assertEquals(SoundProfile.Sound, SoundProfile.Vibrate.next(canSilence = false))
    }

    @Test
    fun ringerModesMapBothWays() {
        SoundProfile.entries.forEach { assertEquals(it, SoundProfile.fromRingerMode(it.ringerMode)) }
        assertEquals(SoundProfile.Sound, SoundProfile.fromRingerMode(42))
    }
}
