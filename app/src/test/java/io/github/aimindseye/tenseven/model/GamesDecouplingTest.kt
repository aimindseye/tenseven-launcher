package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.onboarding.Setup2Plan
import org.junit.Assert.assertEquals
import org.junit.Test

class GamesDecouplingTest {
    private val legacy = "io.github.aimindseye.tenseven/io.github.aimindseye.tenseven.extras.games.SnakeActivity"
    private val external = "org.example.game/org.example.game.MainActivity"
    private val tools = setOf("tenseven_phone", "tenseven_calendar", "tasks", "notes", "calculator", "profiles")

    @Test
    fun fullAndCustomSetupRetainAllSixNonGameTools() {
        assertEquals(tools, Setup2Plan.full())
        assertEquals(setOf("tasks", "notes", "calculator"), Setup2Plan.essentials)
        assertEquals(tools, Setup2Plan.normalizedCustom(tools + setOf("snake", "sudoku")))
    }

    @Test
    fun legacyAndExternalLetterShortcutsRoundTripWithoutDeletionOrRedirection() {
        val saved =
            LetterShortcuts.EMPTY
                .assign('s', legacy)
                .assign('g', external)
                .encode()
        val loaded = LetterShortcuts.decode(saved)
        assertEquals(legacy, loaded.componentFor('s'))
        assertEquals(external, loaded.componentFor('g'))
        assertEquals(saved, loaded.encode())
    }

    @Test
    fun unavailableBundledEntryDropsFromLiveGridWhileExternalGameAndSavedOrderRemain() {
        val saved = listOf(legacy, external)
        assertEquals(saved, HomeApps.resolve(saved, null, emptyList()))
        assertEquals(listOf(external), AppGrid.resolve(saved, emptyList(), listOf(external)))
        assertEquals(listOf(legacy, external), saved)
    }
}
