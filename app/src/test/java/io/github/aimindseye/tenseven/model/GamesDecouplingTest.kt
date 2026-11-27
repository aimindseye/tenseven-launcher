package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.companions.CompanionCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class GamesDecouplingTest {
    private val legacy = "io.github.aimindseye.tenseven/io.github.aimindseye.tenseven.extras.games.SnakeActivity"
    private val external = "org.example.game/org.example.game.MainActivity"

    @Test
    fun fullAndLiteSetupOfferNoGamesAndNormalizeDropsRemovedOnes() {
        val tools = setOf("phone", "calendar", "tasks", "notes", "calculator", "profiles", "watch")
        assertEquals(tools, CompanionCatalog.fullPreset)
        assertEquals(setOf("tasks", "notes", "calculator"), CompanionCatalog.litePreset)
        assertEquals(emptySet<String>(), CompanionCatalog.basePreset)
        assertEquals(setOf("tasks", "notes"), CompanionCatalog.normalize(listOf("tasks", "notes", "snake", "sudoku")))
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
