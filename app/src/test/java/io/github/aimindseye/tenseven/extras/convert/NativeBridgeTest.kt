// Ported from the Rust tests in SableOS convert-core and convert-jni (Apache License 2.0).
package io.github.aimindseye.tenseven.extras.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeTest {
    @Test
    fun referenceConversions() {
        assertEquals("OK:8047", NativeBridge.convertMilli(5_000, 6, 3))
        assertEquals("OK:1000000", NativeBridge.convertMilli(1_000, 8, 7))
        assertEquals("OK:1000000", NativeBridge.convertMilli(1_000, 15, 14))
    }

    @Test
    fun temperaturesAndBounds() {
        assertEquals("OK:32000", NativeBridge.convertMilli(0, 11, 12))
        assertEquals("OK:0", NativeBridge.convertMilli(273_150, 13, 11))
        assertEquals("ERR:OUT_OF_RANGE", NativeBridge.convertMilli(-1, 13, 11))
        assertEquals("ERR:OUT_OF_RANGE", NativeBridge.convertMilli(-273_151, 11, 12))
    }

    @Test
    fun badInputFailsClosed() {
        assertEquals("ERR:INVALID_UNIT", NativeBridge.convertMilli(1_000, -1, 3))
        assertEquals("ERR:INVALID_UNIT", NativeBridge.convertMilli(1_000, 0, 18))
        assertEquals("ERR:INCOMPATIBLE_UNITS", NativeBridge.convertMilli(1_000, 0, 8))
        assertEquals("ERR:OUT_OF_RANGE", NativeBridge.convertMilli(Long.MAX_VALUE, 6, 0))
    }

    @Test
    fun selfTestPasses() = assertTrue(NativeBridge.selfTest().startsWith("PASS:"))
}
