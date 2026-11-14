package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraAppsTest {
    @Test
    fun catalogueHasExactlySixTools() {
        assertEquals(
            setOf("tenseven_phone", "tenseven_calendar", "calculator", "tasks", "notes", "profiles"),
            ExtraApps.ofGroup(ExtraGroup.Tools).map { it.id }.toSet(),
        )
        assertEquals(
            ExtraApps.ALL.size,
            ExtraApps.ALL
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(
            ExtraApps.ALL.size,
            ExtraApps.ALL
                .map { it.className }
                .toSet()
                .size,
        )
    }

    @Test
    fun tasksIsIndependentlySelectableAlongsideNotes() {
        val tasks = ExtraApps.ALL.single { it.id == "tasks" }
        val notes = ExtraApps.ALL.single { it.id == "notes" }
        assertEquals(AppGlyph.TASKS, tasks.glyph)
        assertFalse(tasks.className == notes.className)
        assertEquals(setOf("tasks", "notes"), ExtraApps.normalize(listOf("tasks", "notes")))
        assertEquals(setOf("tasks") to emptySet<String>(), ExtraApps.changes(setOf("notes"), setOf("notes", "tasks")))
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
        val (on, off) = ExtraApps.changes(setOf("notes", "snake"), setOf("tasks", "snake", "sudoku", "bogus"))
        assertEquals(setOf("tasks"), on)
        assertEquals(setOf("notes"), off)
    }

    @Test
    fun obsoleteGameSelectionsDropWithoutMutatingTheSavedInput() {
        val saved = setOf("snake", "sudoku", "notes", "tenseven_calendar")
        assertEquals(setOf("notes", "tenseven_calendar"), ExtraApps.normalize(saved))
        assertEquals(setOf("snake", "sudoku", "notes", "tenseven_calendar"), saved)
        assertFalse(ExtraApps.isExtra("io.github.aimindseye.tenseven.extras.games.SnakeActivity"))
        assertFalse(ExtraApps.isExtra("org.example.games.SnakeActivity"))
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
