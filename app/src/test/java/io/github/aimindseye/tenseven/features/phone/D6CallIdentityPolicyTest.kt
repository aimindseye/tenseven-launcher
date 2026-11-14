package io.github.aimindseye.tenseven.features.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class D6CallIdentityPolicyTest {
    @Test fun realNumberIsRetainedAfterCallRemoval() {
        val saved = D6CallIdentityPolicy.publicNumber(" +1 555 0100 ")
        assertEquals("+1 555 0100", saved)
        assertEquals("+1 555 0100", D6CallIdentityPolicy.displayNumber(saved))
    }

    @Test fun restrictedNumberRemainsPrivate() {
        for (value in listOf(null, "", "Private number", "restricted", "unknown")) {
            assertNull(D6CallIdentityPolicy.publicNumber(value))
        }
        assertEquals("Private number", D6CallIdentityPolicy.displayNumber(null))
        assertEquals("Private caller", D6CallIdentityPolicy.PRIVATE_LABEL)
    }

    @Test fun disconnectRedactionPreservesCallerSnapshot() {
        val start = D6CallIdentityPolicy.reconcile(D6CallIdentityPolicy.Snapshot(null, null), "555-0100", "Alex", true)
        val disconnected = D6CallIdentityPolicy.reconcile(start, null, null, false)
        assertEquals("555-0100", disconnected.number)
        assertEquals("Alex", disconnected.networkName)
    }

    @Test fun nextPrivateCallNeverLeaksPreviousIdentity() {
        val old = D6CallIdentityPolicy.Snapshot("555-0100", "Alex")
        val newCall = D6CallIdentityPolicy.reconcile(old, null, null, true)
        assertNull(newCall.number)
        assertNull(newCall.networkName)
    }

    @Test fun contactNameThenNetworkNameThenNumber() {
        assertEquals("Alex", D6CallIdentityPolicy.callerName("Alex", "Network", "555"))
        assertEquals("Network", D6CallIdentityPolicy.callerName("", "Network", "555"))
        assertEquals("555", D6CallIdentityPolicy.callerName(null, null, "555"))
    }
}
