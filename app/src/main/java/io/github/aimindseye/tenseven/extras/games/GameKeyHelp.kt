package io.github.aimindseye.tenseven.extras.games

/** The keys of each game, shown by the Menu key (English, like the rest of Tenseven's own text). Pure, unit tested. */
internal object GameKeyHelp {
    private const val MOVE = "Arrow keys: move"
    private const val NEW = "N: new game"
    private const val LEAVE = "Esc or Back: leave"

    fun lines(game: GameKind): List<String> =
        when (game) {
            GameKind.Sudoku -> listOf(MOVE, "1 to 9, or W E R S D F Z X C: enter a number", "0 or Del: erase", "U: undo", NEW, LEAVE)
            GameKind.Minesweeper -> listOf(MOVE, "Space or Enter: reveal", "F or G: flag", NEW, LEAVE)
            GameKind.Game2048 -> listOf("Arrow keys: slide the tiles", NEW, LEAVE)
            GameKind.Bricks -> listOf("Left and Right: move the paddle", "Space or Enter: launch the ball", "P: pause", NEW, LEAVE)
            GameKind.Snake -> listOf("Arrow keys: turn", "Space or Enter: start or restart", "P: pause", NEW, LEAVE)
            GameKind.Blocks -> listOf("Left and Right: move, Up: rotate, Down: lower", "Space or Enter: drop", "P: pause", NEW, LEAVE)
            GameKind.Reversi -> listOf(MOVE, "Space or Enter: place a disc", NEW, LEAVE)
            GameKind.Solitaire -> listOf(MOVE, "Enter: pick up or put down", "Space: turn a stock card", "A: send to a foundation", "Del: drop the pick", NEW, LEAVE)
            GameKind.Mahjong -> listOf(MOVE, "Space or Enter: pick a tile", "H: show a move", NEW, LEAVE)
            GameKind.Words -> listOf("Letters: type", "Del: erase", "Enter: guess, or start over", LEAVE)
        }

    fun kindOf(name: String): GameKind? =
        when (name) {
            "sudoku" -> GameKind.Sudoku
            "minesweeper" -> GameKind.Minesweeper
            "2048" -> GameKind.Game2048
            "bricks" -> GameKind.Bricks
            "snake" -> GameKind.Snake
            "blocks" -> GameKind.Blocks
            "reversi" -> GameKind.Reversi
            "solitaire" -> GameKind.Solitaire
            "mahjong" -> GameKind.Mahjong
            "words" -> GameKind.Words
            else -> null
        }
}
