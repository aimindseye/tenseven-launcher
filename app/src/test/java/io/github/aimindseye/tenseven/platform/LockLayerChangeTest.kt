package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class LockLayerChangeTest {
    @Test
    fun equalUpdatesAreSilentAndRemovingListenersStopsDelivery() {
        LockLayerState.update(emptyMap())
        var calls = 0
        val listener = {
            calls++
            Unit
        }
        LockLayerState.addChangeListener(listener)
        LockLayerState.addChangeListener(listener)
        try {
            LockLayerState.update(mapOf("one" to 1))
            LockLayerState.update(mapOf("one" to 1))
            assertEquals(1, calls)
            LockLayerState.removeChangeListener(listener)
            LockLayerState.update(mapOf("one" to 2))
            assertEquals(1, calls)
        } finally {
            LockLayerState.removeChangeListener(listener)
            LockLayerState.update(emptyMap())
        }
    }

    @Test
    fun callerMutationCannotSilentlyChangePublishedCounts() {
        val input = mutableMapOf("one" to 1)
        try {
            LockLayerState.update(input)
            input["one"] = 2
            assertEquals(mapOf("one" to 1), LockLayerState.counts)
            LockLayerState.update(input)
            assertEquals(mapOf("one" to 2), LockLayerState.counts)
        } finally {
            LockLayerState.update(emptyMap())
        }
    }
}
