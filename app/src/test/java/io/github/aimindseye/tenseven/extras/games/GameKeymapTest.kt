// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/games/src/test),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.games

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameKeymapTest {
    private fun cmd(
        game: GameKind,
        keyCode: Int,
        char: Char? = null,
        alt: Boolean = false,
        ctrl: Boolean = false,
        repeat: Int = 0,
    ) = GameKeymap.command(game, GameKeyPress(keyCode, char, alt, ctrl, repeat))

    @Test
    fun trackpadArrowsMoveInEveryGame() {
        GameKind.entries.forEach { game ->
            assertEquals(GameCommand.Move(0, -1), cmd(game, KeyEvent.KEYCODE_DPAD_UP))
            assertEquals(GameCommand.Move(1, 0), cmd(game, KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 3))
        }
    }

    @Test
    fun sudokuDigitsFromAltLayerAndLetterKeys() {
        // Alt+W produces '1' on the Q25.
        assertEquals(GameCommand.Digit(1), cmd(GameKind.Sudoku, KeyEvent.KEYCODE_W, '1', alt = true))
        assertEquals(GameCommand.Digit(9), cmd(GameKind.Sudoku, KeyEvent.KEYCODE_C, 'c'))
        assertEquals(GameCommand.Digit(5), cmd(GameKind.Sudoku, KeyEvent.KEYCODE_5, '5'))
        assertEquals(GameCommand.Erase, cmd(GameKind.Sudoku, KeyEvent.KEYCODE_DEL))
        assertEquals(GameCommand.Undo, cmd(GameKind.Sudoku, KeyEvent.KEYCODE_U, 'u'))
        assertEquals(GameCommand.NewGame, cmd(GameKind.Sudoku, KeyEvent.KEYCODE_N, 'n'))
        // Alt+letter that is not a digit types a symbol, not a command.
        assertNull(cmd(GameKind.Sudoku, KeyEvent.KEYCODE_U, '_', alt = true))
    }

    @Test
    fun minesweeperRevealAndFlag() {
        assertEquals(GameCommand.Primary, cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_SPACE, ' '))
        assertEquals(GameCommand.Primary, cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_ENTER))
        assertEquals(GameCommand.Flag, cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_F, 'f'))
        assertNull(cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_W, 'w'))
    }

    @Test
    fun heldKeysRepeatOnlyMovesAndCtrlIsLeftAlone() {
        assertNull(cmd(GameKind.Sudoku, KeyEvent.KEYCODE_W, 'w', repeat = 1))
        assertNull(cmd(GameKind.Game2048, KeyEvent.KEYCODE_DPAD_UP, ctrl = true))
    }

    @Test
    fun cursorClampsAndSlideDirections() {
        assertEquals(0 to 0, GameKeymap.moveCursor(0, 0, GameCommand.Move(-1, -1), 9))
        assertEquals(8 to 3, GameKeymap.moveCursor(8, 2, GameCommand.Move(1, 1), 9))
        assertEquals(0, GameKeymap.slideDirection(GameCommand.Move(-1, 0)))
        assertEquals(1, GameKeymap.slideDirection(GameCommand.Move(1, 0)))
        assertEquals(2, GameKeymap.slideDirection(GameCommand.Move(0, -1)))
        assertEquals(3, GameKeymap.slideDirection(GameCommand.Move(0, 1)))
    }

    @Test
    fun arcadeGamesStartWithPrimaryAndPauseWithP() {
        listOf(GameKind.Bricks, GameKind.Snake, GameKind.Blocks).forEach { game ->
            assertEquals(GameCommand.Primary, cmd(game, KeyEvent.KEYCODE_SPACE, ' '))
            assertEquals(GameCommand.Primary, cmd(game, KeyEvent.KEYCODE_ENTER))
            assertEquals(GameCommand.Primary, cmd(game, KeyEvent.KEYCODE_DPAD_CENTER))
            assertEquals(GameCommand.Pause, cmd(game, KeyEvent.KEYCODE_P, 'p'))
            assertEquals(GameCommand.NewGame, cmd(game, KeyEvent.KEYCODE_N, 'n'))
            assertNull(cmd(game, KeyEvent.KEYCODE_P, 'p', alt = true))
            assertNull(cmd(game, KeyEvent.KEYCODE_Q, 'q'))
        }
    }

    @Test
    fun reversiAndGame2048UseNewGameAndReversiPrimary() {
        assertEquals(GameCommand.Primary, cmd(GameKind.Reversi, KeyEvent.KEYCODE_ENTER))
        assertEquals(GameCommand.Primary, cmd(GameKind.Reversi, KeyEvent.KEYCODE_SPACE, ' '))
        assertEquals(GameCommand.NewGame, cmd(GameKind.Reversi, KeyEvent.KEYCODE_N, 'n'))
        assertNull(cmd(GameKind.Reversi, KeyEvent.KEYCODE_ENTER, alt = true))
        assertEquals(GameCommand.NewGame, cmd(GameKind.Game2048, KeyEvent.KEYCODE_N, 'n'))
        assertNull(cmd(GameKind.Game2048, KeyEvent.KEYCODE_ENTER))
        assertNull(cmd(GameKind.Game2048, KeyEvent.KEYCODE_N, 'n', alt = true))
    }

    @Test
    fun solitaireKeys() {
        assertEquals(GameCommand.Draw, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_SPACE, ' '))
        assertEquals(GameCommand.Primary, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_ENTER))
        assertEquals(GameCommand.Primary, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(GameCommand.Auto, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_A, 'a'))
        assertEquals(GameCommand.Erase, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_DEL))
        assertEquals(GameCommand.Erase, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_ESCAPE))
        assertEquals(GameCommand.NewGame, cmd(GameKind.Solitaire, KeyEvent.KEYCODE_N, 'n'))
        assertNull(cmd(GameKind.Solitaire, KeyEvent.KEYCODE_A, 'a', alt = true))
    }

    @Test
    fun mahjongKeys() {
        assertEquals(GameCommand.Primary, cmd(GameKind.Mahjong, KeyEvent.KEYCODE_ENTER))
        assertEquals(GameCommand.Hint, cmd(GameKind.Mahjong, KeyEvent.KEYCODE_H, 'h'))
        assertEquals(GameCommand.NewGame, cmd(GameKind.Mahjong, KeyEvent.KEYCODE_N, 'n'))
        assertNull(cmd(GameKind.Mahjong, KeyEvent.KEYCODE_H, 'h', alt = true))
    }

    @Test
    fun wordsTypeEveryLetterIncludingN() {
        assertEquals(GameCommand.Letter('N'), cmd(GameKind.Words, KeyEvent.KEYCODE_N, 'n'))
        assertEquals(GameCommand.Letter('Q'), cmd(GameKind.Words, KeyEvent.KEYCODE_Q, 'Q'))
        assertEquals(GameCommand.Erase, cmd(GameKind.Words, KeyEvent.KEYCODE_DEL))
        assertEquals(GameCommand.Primary, cmd(GameKind.Words, KeyEvent.KEYCODE_ENTER))
        assertEquals(GameCommand.Primary, cmd(GameKind.Words, KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(cmd(GameKind.Words, KeyEvent.KEYCODE_1, '1'))
        assertNull(cmd(GameKind.Words, KeyEvent.KEYCODE_A, 'a', alt = true))
        assertNull(cmd(GameKind.Words, KeyEvent.KEYCODE_SPACE, ' '))
    }

    @Test
    fun sudokuZeroAndForwardDeleteErase() {
        assertEquals(GameCommand.Erase, cmd(GameKind.Sudoku, KeyEvent.KEYCODE_0, '0'))
        assertEquals(GameCommand.Erase, cmd(GameKind.Sudoku, KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(GameCommand.Flag, cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_G, 'g'))
        assertEquals(GameCommand.Primary, cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(cmd(GameKind.Minesweeper, KeyEvent.KEYCODE_F, 'f', alt = true))
    }
}
