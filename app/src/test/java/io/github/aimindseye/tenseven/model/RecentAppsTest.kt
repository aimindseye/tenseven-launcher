package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    private fun at(
        pkg: String,
        time: Long,
    ) = ForegroundEvent(pkg, time)

    @Test
    fun mostRecentFirstEachAppOnce() {
        val events = listOf(at("a", 1), at("b", 5), at("a", 9), at("c", 3))
        assertEquals(listOf("a", "b", "c"), RecentApps.select(events, emptySet()))
    }

    @Test
    fun excludedAndBlankPackagesAreLeftOut() {
        val events = listOf(at("home", 9), at("", 8), at("a", 7), at("systemui", 6))
        assertEquals(listOf("a"), RecentApps.select(events, setOf("home", "systemui")))
    }

    @Test
    fun capsTheListAndHandlesNoEvents() {
        val many = (1..30).map { at("p$it", it.toLong()) }
        assertEquals(RecentApps.MAX, RecentApps.select(many, emptySet()).size)
        assertEquals(listOf("p30", "p29"), RecentApps.select(many, emptySet(), limit = 2))
        assertEquals(emptyList<String>(), RecentApps.select(emptyList(), emptySet()))
    }
}
