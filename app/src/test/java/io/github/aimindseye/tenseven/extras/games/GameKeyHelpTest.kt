package io.github.aimindseye.tenseven.extras.games

import io.github.aimindseye.tenseven.model.ExtraApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameKeyHelpTest {
    @Test
    fun everyGameListsItsKeysAndHowToLeave() {
        GameKind.entries.forEach { kind ->
            val lines = GameKeyHelp.lines(kind)
            assertTrue("$kind", lines.size >= 3)
            assertTrue("$kind", lines.any { it.contains("leave") })
        }
    }

    @Test
    fun everyCatalogueGameMapsToAKind() {
        // The activity names the game by a string; each of the ten has help.
        listOf("sudoku", "minesweeper", "2048", "bricks", "snake", "blocks", "reversi", "solitaire", "mahjong", "words")
            .forEach { assertNotNull(it, GameKeyHelp.kindOf(it)) }
        assertEquals(10, ExtraApps.ALL.count { it.className.contains(".games.") })
        assertEquals(null, GameKeyHelp.kindOf("pong"))
    }
}
