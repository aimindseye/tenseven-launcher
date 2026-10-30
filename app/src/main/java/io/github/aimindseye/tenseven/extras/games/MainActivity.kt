// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/games/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.games

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.SableGlobalTheme

/** The key host of the running game; screens install their handler in it. */
internal val LocalGameKeyHost = staticCompositionLocalOf { GameKeyHost() }

/** One game per launcher entry: the subclasses in GameActivities.kt each name their game. */
abstract class GameActivity(
    private val game: String,
) : ComponentActivity() {
    private val keyHost = GameKeyHost()

    /** Key codes whose key-down was taken by the game, so their key-up is swallowed too. */
    private val consumedKeyCodes = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DesignText.t.bindTo(this)
        val qualification = NativeBridge.selfTest()
        check(qualification.startsWith("PASS:")) { qualification }

        setContent {
            SableGlobalTheme(window = window) {
                CompositionLocalProvider(LocalGameKeyHost provides keyHost) {
                    when (game) {
                        "sudoku" -> SudokuScreen()
                        "minesweeper" -> MinesweeperScreen()
                        "2048" -> Game2048Screen()
                        "bricks" -> BricksScreen()
                        "snake" -> SnakeScreen()
                        "blocks" -> BlocksScreen()
                        "reversi" -> ReversiScreen()
                        "solitaire" -> SolitaireScreen()
                        "mahjong" -> MahjongScreen()
                        "words" -> WordsScreen()
                        else -> error("Unsupported game: $game")
                    }
                }
            }
        }
    }

    // ComponentActivity.dispatchKeyEvent is a public framework override; lint flags it as androidx-restricted.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val taken =
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    (keyHost.handler?.invoke(event.toGameKeyPress()) == true).also { taken ->
                        if (taken) consumedKeyCodes += event.keyCode
                    }
                }

                KeyEvent.ACTION_UP -> {
                    consumedKeyCodes.remove(event.keyCode)
                }

                else -> {
                    false
                }
            }
        return taken || super.dispatchKeyEvent(event)
    }
}
