// Ported from the Rust tests in SableOS calculator-core and calculator-jni (Apache License 2.0).
package io.github.aimindseye.tenseven.extras.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeTest {
    @Test
    fun arithmeticStaysExact() {
        assertEquals("OK:3/10", NativeBridge.applyBinary("0.1", 0, "0.2"))
        assertEquals("OK:1/4", NativeBridge.applyBinary("1", 3, "4"))
        assertEquals("OK:-3/2", NativeBridge.applyBinary("-2.50", 0, "1"))
        assertEquals("OK:3/4", NativeBridge.applyBinary("1", 1, "0.25"))
        assertEquals("OK:1/1", NativeBridge.applyBinary("0.25", 2, "4"))
    }

    @Test
    fun errorsFailClosed() {
        assertEquals("ERR:INVALID_OPERATION", NativeBridge.applyBinary("1", 99, "2"))
        assertEquals("ERR:INVALID_NUMBER", NativeBridge.applyBinary("invalid", 0, "2"))
        assertEquals("ERR:INVALID_NUMBER", NativeBridge.applyBinary("1.", 0, "2"))
        assertEquals("ERR:INVALID_NUMBER", NativeBridge.applyBinary("1.2.3", 0, "2"))
        assertEquals("ERR:DIVIDE_BY_ZERO", NativeBridge.applyBinary("1", 3, "0"))
        assertEquals("ERR:OVERFLOW", NativeBridge.applyBinary("170141183460469231731687303715884105728", 0, "1"))
    }

    @Test
    fun scientificProtocolIsStable() {
        assertEquals("OK:3", NativeBridge.applyScientific("9", 0))
        assertEquals("OK:144", NativeBridge.applyScientific("12", 1))
        assertEquals("OK:0.5", NativeBridge.applyScientific("30", 3))
        assertEquals("OK:2", NativeBridge.applyScientific("100", 7))
        assertEquals("ERR:DOMAIN", NativeBridge.applyScientific("-1", 0))
        assertEquals("ERR:DIVIDE_BY_ZERO", NativeBridge.applyScientific("0", 2))
        assertEquals("ERR:DOMAIN", NativeBridge.applyScientific("0", 6))
        assertEquals("ERR:INVALID_OPERATION", NativeBridge.applyScientific("1", 8))
    }

    @Test
    fun selfTestPasses() = assertTrue(NativeBridge.selfTest().startsWith("PASS:"))
}
