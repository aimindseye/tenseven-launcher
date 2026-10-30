package io.github.aimindseye.tenseven.model

enum class ExtraGroup { Tools, Games }

/** One optional app that ships inside Tenseven; [className] is its launcher activity. */
data class ExtraApp(
    val id: String,
    val label: String,
    val summary: String,
    val className: String,
    val glyph: AppGlyph,
    val group: ExtraGroup,
)

/**
 * The extra apps that come with Tenseven (ported from SableOS): Calculator with a unit converter, Notes, and ten
 * games. None is on by default. Setup lets the user turn each on; an app that is off has no launcher entry. Pure
 * policy, unit tested.
 */
object ExtraApps {
    private const val ROOT = "io.github.aimindseye.tenseven.extras."

    val ALL: List<ExtraApp> =
        listOf(
            ExtraApp("calculator", "Calc Plus", "Exact arithmetic, scientific functions and unit conversion", ROOT + "calculator.MainActivity", AppGlyph.CALCULATOR, ExtraGroup.Tools),
            ExtraApp("notes", "Notes", "Notes and checklists kept on the phone; share text into a new note", ROOT + "notes.MainActivity", AppGlyph.NOTES, ExtraGroup.Tools),
            ExtraApp("sudoku", "Sudoku", "Number puzzle", ROOT + "games.SudokuActivity", AppGlyph.SUDOKU, ExtraGroup.Games),
            ExtraApp("minesweeper", "Minesweeper", "Clear the board without hitting a mine", ROOT + "games.MinesweeperActivity", AppGlyph.MINESWEEPER, ExtraGroup.Games),
            ExtraApp("game2048", "2048", "Slide and merge the tiles", ROOT + "games.Game2048Activity", AppGlyph.GAME_2048, ExtraGroup.Games),
            ExtraApp("bricks", "Bricks", "Break the bricks with the ball", ROOT + "games.BricksActivity", AppGlyph.BRICKS, ExtraGroup.Games),
            ExtraApp("snake", "Snake", "Eat and grow without hitting yourself", ROOT + "games.SnakeActivity", AppGlyph.SNAKE, ExtraGroup.Games),
            ExtraApp("blocks", "Blocks", "Falling blocks", ROOT + "games.BlocksActivity", AppGlyph.BLOCKS, ExtraGroup.Games),
            ExtraApp("reversi", "Reversi", "Flip the board to your colour", ROOT + "games.ReversiActivity", AppGlyph.REVERSI, ExtraGroup.Games),
            ExtraApp("solitaire", "Solitaire", "Klondike with the keyboard", ROOT + "games.SolitaireActivity", AppGlyph.SOLITAIRE, ExtraGroup.Games),
            ExtraApp("mahjong", "Mahjong", "Match the tiles", ROOT + "games.MahjongActivity", AppGlyph.MAHJONG, ExtraGroup.Games),
            ExtraApp("words", "Words", "Guess the five-letter word in six tries", ROOT + "games.WordsActivity", AppGlyph.WORDS, ExtraGroup.Games),
        )

    private val byClass = ALL.associateBy { it.className }

    fun byClassName(className: String): ExtraApp? = byClass[className]

    fun isExtra(className: String): Boolean = className in byClass

    fun ofGroup(group: ExtraGroup): List<ExtraApp> = ALL.filter { it.group == group }

    /** Drops unknown ids; the default (nothing chosen) is the empty set. */
    fun normalize(ids: Collection<String>?): Set<String> = ids.orEmpty().filter { id -> ALL.any { it.id == id } }.toSet()

    /** What to switch on and off to move from [current] to [wanted], by id. */
    fun changes(
        current: Set<String>,
        wanted: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val target = normalize(wanted)
        val now = normalize(current)
        return (target - now) to (now - target)
    }
}
