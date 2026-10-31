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

    private fun resumed(
        pkg: String,
        cls: String,
        time: Long,
    ) = UsageSample(pkg, cls, resumed = true, timeMs = time)

    private fun left(
        pkg: String,
        cls: String,
        time: Long,
    ) = UsageSample(pkg, cls, resumed = false, timeMs = time)

    @Test
    fun aBlinkOfAnActivityIsNotOpening() {
        val samples =
            listOf(
                resumed("mail", "Main", 1_000),
                left("mail", "Main", 60_000),
                // A permission prompt another app flashes up for half a second.
                resumed("permissioncontroller", "Grant", 61_000),
                left("permissioncontroller", "Grant", 61_500),
            )
        assertEquals(listOf("mail"), RecentApps.opened(samples).map { it.packageName })
    }

    @Test
    fun theAppInFrontNowCounts() {
        val samples = listOf(resumed("maps", "Main", 5_000))
        assertEquals(listOf(at("maps", 5_000)), RecentApps.opened(samples))
    }

    @Test
    fun eachActivityIsMeasuredOnItsOwn() {
        val samples =
            listOf(
                resumed("a", "One", 0),
                resumed("a", "Two", 100),
                left("a", "Two", 300),
                left("a", "One", 9_000),
            )
        assertEquals(listOf(at("a", 0)), RecentApps.opened(samples))
    }

    @Test
    fun aLeaveWithoutAnEarlierResumeIsIgnored() {
        assertEquals(emptyList<ForegroundEvent>(), RecentApps.opened(listOf(left("a", "One", 5_000))))
    }
}
