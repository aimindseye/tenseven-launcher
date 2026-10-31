package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LockLayerTest {
    @Test
    fun entriesOrderByCountThenPackage() {
        val result = LockLayer.entries(mapOf("b" to 2, "a" to 2, "c" to 5))
        assertEquals(listOf("c", "a", "b"), result.map { it.packageName })
    }

    @Test
    fun entriesDropExcludedAndEmptyAndCap() {
        val counts = (1..10).associate { "p$it" to it } + ("zero" to 0)
        val result = LockLayer.entries(counts, excluded = setOf("p10"), max = 3)
        assertEquals(listOf("p9", "p8", "p7"), result.map { it.packageName })
    }

    @Test
    fun countLabelCapsAtNinetyNine() {
        assertEquals("7", LockLayer.countLabel(7))
        assertEquals("99", LockLayer.countLabel(99))
        assertEquals("99+", LockLayer.countLabel(100))
    }

    @Test
    fun tallySkipsOngoingSummariesAndOwnApp() {
        val result =
            LockLayer.tally(
                listOf(
                    Triple("chat", false, false),
                    Triple("chat", false, false),
                    Triple("chat", true, false),
                    Triple("chat", false, true),
                    Triple("self", false, false),
                    Triple("mail", false, false),
                ),
                ownPackage = "self",
            )
        assertEquals(mapOf("chat" to 2, "mail" to 1), result)
    }
}
