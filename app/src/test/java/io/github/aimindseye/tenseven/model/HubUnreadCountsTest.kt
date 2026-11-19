package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.live.AppUnreadCount
import org.junit.Assert.assertEquals
import org.junit.Test

class HubUnreadCountsTest {
    private val hub = listOf(AppUnreadCount("org.hub", 7))

    @Test
    fun waitingCountsComeFirstMostFirst() {
        val picked =
            HubUnreadCounts.pick(
                mapOf("b.app" to 2, "a.app" to 2, "c.app" to WAITING, "zero.app" to 0),
                MAX_APPS,
            ) { hub }
        assertEquals(listOf("c.app", "a.app", "b.app"), picked.map { it.packageName })
    }

    @Test
    fun theHubCountsWhenNothingIsWaiting() {
        assertEquals(hub, HubUnreadCounts.pick(mapOf("zero.app" to 0), MAX_APPS) { hub })
        assertEquals(hub, HubUnreadCounts.pick(emptyMap(), MAX_APPS) { hub })
    }

    @Test
    fun capsTheNumberOfApps() {
        val many = (1..MANY_APPS).associate { "app$it" to it }
        assertEquals(CAP, HubUnreadCounts.pick(many, CAP) { hub }.size)
    }

    private companion object {
        const val MAX_APPS = 32
        const val WAITING = 5
        const val MANY_APPS = 40
        const val CAP = 3
    }
}
