package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.live.AppUnreadCount
import org.junit.Assert.assertEquals
import org.junit.Test

class HubUnreadCountsTest {
    private val hub = listOf(AppUnreadCount("org.hub", 7))

    @Test
    fun waitingCountsComeFirstMostFirst() {
        val picked = HubUnreadCounts.pick(mapOf("b.app" to 2, "a.app" to 2, "c.app" to 5, "zero.app" to 0), 32) { hub }
        assertEquals(listOf("c.app", "a.app", "b.app"), picked.map { it.packageName })
    }

    @Test
    fun theHubCountsWhenNothingIsWaiting() {
        assertEquals(hub, HubUnreadCounts.pick(mapOf("zero.app" to 0), 32) { hub })
        assertEquals(hub, HubUnreadCounts.pick(emptyMap(), 32) { hub })
    }

    @Test
    fun capsTheNumberOfApps() {
        val many = (1..40).associate { "app$it" to it }
        assertEquals(3, HubUnreadCounts.pick(many, 3) { hub }.size)
    }
}
