package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentLaunchesTest {
    @Test
    fun roundTrips() {
        val events = listOf(ForegroundEvent("a.b", 9), ForegroundEvent("c.d", 3))
        assertEquals(events, RecentLaunches.decode(RecentLaunches.encode(events)))
        assertEquals(emptyList<ForegroundEvent>(), RecentLaunches.decode(null))
        assertEquals(emptyList<ForegroundEvent>(), RecentLaunches.decode("garbage"))
    }

    @Test
    fun keepsOneEntryPerAppNewestFirst() {
        var events = emptyList<ForegroundEvent>()
        events = RecentLaunches.record(events, "a", 1)
        events = RecentLaunches.record(events, "b", 2)
        events = RecentLaunches.record(events, "a", 3)
        assertEquals(listOf("a", "b"), events.map { it.packageName })
        assertEquals(3L, events.first().timeMs)
    }

    @Test
    fun capsTheList() {
        var events = emptyList<ForegroundEvent>()
        repeat(40) { events = RecentLaunches.record(events, "p$it", it.toLong()) }
        assertEquals(RecentLaunches.KEEP, events.size)
        assertEquals("p39", events.first().packageName)
    }

    @Test
    fun mergesWithUsageEventsByTime() {
        val launches = listOf(ForegroundEvent("fresh", 100))
        val usage = listOf(ForegroundEvent("old", 50), ForegroundEvent("fresh", 40))
        assertEquals(listOf("fresh", "old"), RecentApps.select(launches + usage, emptySet()))
    }
}
