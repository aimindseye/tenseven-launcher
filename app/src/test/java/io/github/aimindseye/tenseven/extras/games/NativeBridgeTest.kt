// Ported from the Rust tests in SableOS games-core and games-jni (Apache License 2.0).
package io.github.aimindseye.tenseven.extras.games

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeTest {
    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    @Test
    fun selfTestPasses() = assertTrue(NativeBridge.selfTest().startsWith("PASS:"))

    @Test
    fun sudokuRejectsConflictsAndFixedEdits() {
        val state = NativeBridge.sudokuStart(puzzle).removePrefix("OK:")
        assertEquals("ERR:FIXED_CELL", NativeBridge.sudokuSet(state, 0, 0, 4))
        assertEquals("ERR:CONFLICT", NativeBridge.sudokuSet(state, 0, 2, 5))
        assertTrue(NativeBridge.sudokuSet(state, 0, 2, 4).startsWith("OK:S1:"))
    }

    @Test
    fun sudokuStateReplaysEntries() {
        val start = NativeBridge.sudokuStart("0".repeat(81)).removePrefix("OK:")
        val first = NativeBridge.sudokuSet(start, 0, 0, 5).removePrefix("OK:")
        val second = NativeBridge.sudokuSet(first, 0, 0, 4).removePrefix("OK:")
        assertTrue(NativeBridge.sudokuBoard(second).startsWith("OK:4"))
        assertEquals("ERR:CONFLICT", NativeBridge.sudokuSet(first, 0, 1, 5))
        assertEquals("ERR:INVALID_STATE", NativeBridge.sudokuBoard("nope"))
        assertEquals("ERR:INVALID_PUZZLE", NativeBridge.sudokuStart("12"))
    }

    @Test
    fun sudokuSolvedOnlyWhenFullAndConsistent() {
        assertTrue(!NativeBridge.sudokuSolved(NativeBridge.sudokuStart(puzzle).removePrefix("OK:")))
    }

    @Test
    fun minesweeperIsDeterministicAndReplayable() {
        val state = NativeBridge.minesweeperStart(9, 9, 10, 1803).removePrefix("OK:")
        val first = NativeBridge.minesweeperReveal(state, 4, 4)
        val second = NativeBridge.minesweeperReveal(state, 4, 4)
        assertEquals(first, second)
        val fields = first.split('|')
        assertTrue(fields.size >= 3)
        assertEquals("OK", fields[0])
        val view = NativeBridge.minesweeperView(fields[1])
        assertTrue(view.startsWith("OK:"))
        assertEquals(81, view.removePrefix("OK:").length)
        assertEquals("ERR:OUT_OF_BOUNDS", NativeBridge.minesweeperReveal(state, 9, 0))
        assertEquals("ERR:INVALID_BOARD", NativeBridge.minesweeperStart(0, 9, 1, 1))
        assertEquals("ERR:INVALID_BOARD", NativeBridge.minesweeperStart(3, 3, 9, 1))
    }

    @Test
    fun minesweeperPlacesTheRequestedMines() {
        val state = NativeBridge.minesweeperStart(9, 9, 10, 1803).removePrefix("OK:")
        var current = state
        var mines = 0
        for (y in 0 until 9) for (x in 0 until 9) {
            val result = NativeBridge.minesweeperReveal(current, x, y)
            if (result.endsWith("|MINE")) mines++
        }
        assertEquals(10, mines)
    }

    @Test
    fun flagsToggle() {
        val state = NativeBridge.minesweeperStart(5, 5, 3, 7).removePrefix("OK:")
        val flagged = NativeBridge.minesweeperToggleFlag(state, 1, 1)
        assertTrue(flagged.endsWith("|FLAGGED"))
        val next = flagged.split('|')[1]
        assertTrue(NativeBridge.minesweeperToggleFlag(next, 1, 1).endsWith("|HIDDEN"))
        assertEquals('F', NativeBridge.minesweeperView(next).removePrefix("OK:")[6])
    }

    @Test
    fun game2048MergesOncePerMove() {
        val result = NativeBridge.game2048Slide(intArrayOf(2, 2, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 0)
        assertArrayEquals(longArrayOf(4, 8, 0, 0), result.copyOfRange(0, 4))
        assertEquals(12L, result[16])
        assertTrue(runCatching { NativeBridge.game2048Slide(IntArray(15), 0) }.isFailure)
        assertTrue(runCatching { NativeBridge.game2048Slide(IntArray(16), 4) }.isFailure)
    }
}
