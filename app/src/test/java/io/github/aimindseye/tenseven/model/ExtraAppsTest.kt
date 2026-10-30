package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraAppsTest {
    @Test
    fun catalogueHasToolsAndTenGames() {
        assertEquals(3, ExtraApps.ofGroup(ExtraGroup.Tools).size)
        assertEquals(10, ExtraApps.ofGroup(ExtraGroup.Games).size)
        assertEquals(ExtraApps.ALL.size, ExtraApps.ALL.map { it.id }.toSet().size)
        assertEquals(ExtraApps.ALL.size, ExtraApps.ALL.map { it.className }.toSet().size)
    }

    @Test
    fun nothingIsOnByDefault() {
        assertTrue(ExtraApps.normalize(null).isEmpty())
    }

    @Test
    fun unknownIdsDrop() {
        assertEquals(setOf("notes"), ExtraApps.normalize(listOf("notes", "bogus")))
    }

    @Test
    fun changesListWhatToSwitch() {
        val (on, off) = ExtraApps.changes(setOf("notes", "snake"), setOf("snake", "sudoku", "bogus"))
        assertEquals(setOf("sudoku"), on)
        assertEquals(setOf("notes"), off)
    }

    @Test
    fun lookupByClass() {
        val calc = ExtraApps.ALL.first { it.id == "calculator" }
        assertNotNull(ExtraApps.byClassName(calc.className))
        assertTrue(ExtraApps.isExtra(calc.className))
        assertNull(ExtraApps.byClassName("io.github.aimindseye.tenseven.SableStartActivity"))
        assertFalse(ExtraApps.isExtra("x"))
    }
}
