package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageTest {
    @Test
    fun launchesAreCountedAndRankedMostUsedFirst() {
        var usage = AppUsage.EMPTY
        repeat(3) { usage = usage.record("a") }
        repeat(5) { usage = usage.record("b") }
        usage = usage.record("c")
        assertEquals(3, usage.score("a"))
        assertEquals(0, usage.score("zz"))
        assertEquals(listOf("b", "a"), usage.mostUsed(2))
    }

    @Test
    fun countsDecayInsteadOfGrowingForever() {
        var usage = AppUsage.EMPTY.record("old")
        repeat(AppUsage.LIMIT) { usage = usage.record("busy") }
        assertEquals(AppUsage.LIMIT, usage.score("busy"))
        usage = usage.record("busy")
        assertTrue(usage.score("busy") <= AppUsage.LIMIT)
        // "old" (one launch) halves to zero and is forgotten.
        assertEquals(0, usage.score("old"))
    }

    @Test
    fun storedTextRoundTripsAndDamageIsDropped() {
        val usage = AppUsage.EMPTY.record("1:a/b").record("1:a/b").record("2:c/d")
        assertEquals(usage.encode(), AppUsage.decode(usage.encode()).encode())
        val damaged = AppUsage.decode("1:a/b\t4\ngarbage\n\t3\n2:c/d\tx\n3:e/f\t-1")
        assertEquals(4, damaged.score("1:a/b"))
        assertEquals(1, damaged.size)
    }

    @Test
    fun blankOrMultilineKeysAreIgnored() {
        assertEquals(0, AppUsage.EMPTY.record(" ").size)
        assertEquals(0, AppUsage.EMPTY.record("a\nb").size)
    }

    @Test
    fun theTableStaysSmall() {
        var usage = AppUsage.EMPTY
        repeat(AppUsage.MAX_APPS + 50) { usage = usage.record("app$it") }
        assertTrue(usage.size <= AppUsage.MAX_APPS)
    }

    @Test
    fun theMostUsedMatchComesFirstWithinATier() {
        val usage = AppUsage.EMPTY.record("Camera").record("Calendar").record("Calendar")
        val apps = listOf("Calculator", "Calendar", "Camera")
        val ranked = CommandRanking.apps(apps, { it }, { null }, "ca", frequency = usage::score)
        assertEquals(listOf("Calendar", "Camera", "Calculator"), ranked)
        // A better tier still beats usage: "Ccleaner" starts with "c", "Music cast" only contains it.
        val tiers = CommandRanking.apps(listOf("Music cast", "Ccleaner"), { it }, { null }, "c", frequency = { if (it == "Music cast") 9 else 0 })
        assertEquals(listOf("Ccleaner", "Music cast"), tiers)
    }
}
