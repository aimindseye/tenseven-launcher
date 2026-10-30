// Ported from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f), sable-src/apps/r8/rust/crates/
// games-core and games-jni (Rust), Apache License 2.0. Changed: written in Kotlin, so the games need no native
// library. The text protocols ("OK:", "ERR:", "S1:" and "M1:" states), the replayed-operations state model and
// the minesweeper mine placement (xorshift64 from the seed) are unchanged, so a seed gives the same board.
package io.github.aimindseye.tenseven.extras.games

/** Game rules for Sudoku, Minesweeper and 2048, answered in the protocol strings the screens already read. */
internal object NativeBridge {
    private const val SUDOKU_PREFIX = "S1"
    private const val MINESWEEPER_PREFIX = "M1"

    fun selfTest(): String {
        val started = sudokuStart("0".repeat(81))
        val state = started.removePrefix("OK:").takeIf { started.startsWith("OK:") } ?: return "FAIL:games-core:start"
        val first = sudokuSet(state, 0, 0, 5)
        val next = first.removePrefix("OK:").takeIf { first.startsWith("OK:") } ?: return "FAIL:games-core:$first"
        return when (val result = sudokuSet(next, 0, 1, 5)) {
            "ERR:CONFLICT" -> "PASS:games-core:sudoku-conflict"
            else -> "FAIL:games-core:$result"
        }
    }

    // ---- Sudoku -------------------------------------------------------------------------------------------

    private enum class SudokuError(val code: String) {
        InvalidPuzzle("ERR:INVALID_PUZZLE"),
        FixedCell("ERR:FIXED_CELL"),
        Conflict("ERR:CONFLICT"),
        OutOfRange("ERR:SUDOKU"),
    }

    private class SudokuFail(val error: SudokuError) : Exception()

    private class SudokuBoard(puzzle: String) {
        val cells = IntArray(81)
        private val fixed = BooleanArray(81)

        init {
            if (puzzle.length != 81 || !puzzle.all { it in '0'..'9' }) throw SudokuFail(SudokuError.InvalidPuzzle)
            puzzle.forEachIndexed { index, c ->
                cells[index] = c - '0'
                fixed[index] = cells[index] != 0
            }
            if (!consistent()) throw SudokuFail(SudokuError.InvalidPuzzle)
        }

        fun set(
            row: Int,
            col: Int,
            value: Int,
        ) {
            if (row !in 0..8 || col !in 0..8 || value !in 0..9) throw SudokuFail(SudokuError.OutOfRange)
            val index = row * 9 + col
            if (fixed[index]) throw SudokuFail(SudokuError.FixedCell)
            val old = cells[index]
            cells[index] = value
            if (!consistent()) {
                cells[index] = old
                throw SudokuFail(SudokuError.Conflict)
            }
        }

        fun solved(): Boolean = cells.all { it != 0 } && consistent()

        private fun consistent(): Boolean {
            for (i in 0..8) {
                if (!validGroup((0..8).map { cells[i * 9 + it] })) return false
                if (!validGroup((0..8).map { cells[it * 9 + i] })) return false
            }
            for (boxRow in 0..2) {
                for (boxCol in 0..2) {
                    val values = (0..8).map { offset -> cells[(boxRow * 3 + offset / 3) * 9 + boxCol * 3 + offset % 3] }
                    if (!validGroup(values)) return false
                }
            }
            return true
        }

        private fun validGroup(values: List<Int>): Boolean {
            val seen = BooleanArray(10)
            for (value in values) {
                if (value == 0) continue
                if (value > 9 || seen[value]) return false
                seen[value] = true
            }
            return true
        }
    }

    fun sudokuStart(puzzle: String): String =
        try {
            SudokuBoard(puzzle)
            "OK:$SUDOKU_PREFIX:$puzzle:"
        } catch (fail: SudokuFail) {
            fail.error.code
        }

    /** The board and the state's puzzle and operations, or null for a state that does not replay. */
    private fun replaySudoku(state: String): Triple<SudokuBoard, String, String>? {
        val fields = state.split(':', limit = 3)
        if (fields.size != 3 || fields[0] != SUDOKU_PREFIX) return null
        val puzzle = fields[1]
        val operations = fields[2]
        return try {
            val board = SudokuBoard(puzzle)
            if (operations.isNotEmpty()) {
                for (encoded in operations.split(';')) {
                    val parts = encoded.split(',')
                    if (parts.size != 3) return null
                    val row = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                    val col = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                    val value = parts[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                    board.set(row, col, value)
                }
            }
            Triple(board, puzzle, operations)
        } catch (_: SudokuFail) {
            null
        }
    }

    fun sudokuSet(
        state: String,
        row: Int,
        col: Int,
        value: Int,
    ): String {
        if (row < 0 || col < 0) return "ERR:OUT_OF_BOUNDS"
        if (value !in 0..9) return "ERR:INVALID_VALUE"
        val (board, puzzle, operations) = replaySudoku(state) ?: return "ERR:INVALID_STATE"
        try {
            board.set(row, col, value)
        } catch (fail: SudokuFail) {
            return fail.error.code
        }
        val encoded = "$row,$col,$value"
        val all = if (operations.isEmpty()) encoded else "$operations;$encoded"
        return "OK:$SUDOKU_PREFIX:$puzzle:$all"
    }

    fun sudokuBoard(state: String): String {
        val (board, _, _) = replaySudoku(state) ?: return "ERR:INVALID_STATE"
        return "OK:" + board.cells.joinToString("")
    }

    fun sudokuSolved(state: String): Boolean = replaySudoku(state)?.first?.solved() ?: false

    // ---- Minesweeper --------------------------------------------------------------------------------------

    private enum class Cell { Hidden, Revealed, Flagged }

    private class MineState(
        val width: Int,
        val height: Int,
        val mines: Int,
        val seed: Long,
        val operations: String,
    ) {
        fun encode() = "$MINESWEEPER_PREFIX:$width:$height:$mines:${java.lang.Long.toUnsignedString(seed)}:$operations"

        fun with(operation: String) = MineState(width, height, mines, seed, if (operations.isEmpty()) operation else "$operations;$operation")
    }

    private class MineFail(val code: String) : Exception()

    private class MineBoard(val width: Int, val height: Int, mineCount: Int, seed: Long) {
        private val mines: BooleanArray
        private val state: Array<Cell>

        init {
            val count = width.toLong() * height.toLong()
            if (count > Int.MAX_VALUE) throw MineFail("ERR:BOARD_TOO_LARGE")
            if (width <= 0 || height <= 0 || mineCount < 0 || mineCount >= count) throw MineFail("ERR:INVALID_BOARD")
            mines = BooleanArray(count.toInt())
            state = Array(count.toInt()) { Cell.Hidden }
            var placed = 0
            var rng = if (java.lang.Long.compareUnsigned(seed, 1) < 0) 1L else seed
            while (placed < mineCount) {
                rng = rng xor (rng shl 13)
                rng = rng xor (rng ushr 7)
                rng = rng xor (rng shl 17)
                val index = java.lang.Long.remainderUnsigned(rng, count).toInt()
                if (!mines[index]) {
                    mines[index] = true
                    placed++
                }
            }
        }

        private fun index(
            x: Int,
            y: Int,
        ): Int {
            if (x !in 0 until width || y !in 0 until height) throw MineFail("ERR:OUT_OF_BOUNDS")
            return y * width + x
        }

        fun toggleFlag(
            x: Int,
            y: Int,
        ): Cell {
            val i = index(x, y)
            state[i] =
                when (state[i]) {
                    Cell.Hidden -> Cell.Flagged
                    Cell.Flagged -> Cell.Hidden
                    Cell.Revealed -> Cell.Revealed
                }
            return state[i]
        }

        /** -2 ignored, -1 mine, otherwise the count of neighbouring mines. */
        fun reveal(
            x: Int,
            y: Int,
        ): Int {
            val i = index(x, y)
            if (state[i] != Cell.Hidden) return IGNORED
            state[i] = Cell.Revealed
            if (mines[i]) return MINE
            return neighbours(x, y)
        }

        private fun neighbours(
            x: Int,
            y: Int,
        ): Int {
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && mines[ny * width + nx]) count++
                }
            }
            return count
        }

        companion object {
            const val IGNORED = -2
            const val MINE = -1
        }
    }

    fun minesweeperStart(
        width: Int,
        height: Int,
        mines: Int,
        seed: Long,
    ): String {
        if (width <= 0 || height <= 0 || mines < 0) return "ERR:INVALID_BOARD"
        if (seed < 0) return "ERR:INVALID_SEED"
        return try {
            MineBoard(width, height, mines, seed)
            "OK:" + MineState(width, height, mines, seed, "").encode()
        } catch (fail: MineFail) {
            fail.code
        }
    }

    private fun decodeMine(encoded: String): MineState? {
        val fields = encoded.split(':', limit = 6)
        if (fields.size != 6 || fields[0] != MINESWEEPER_PREFIX) return null
        val width = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val height = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val mines = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val seed = fields[4].toULongOrNull()?.toLong() ?: return null
        return MineState(width, height, mines, seed, fields[5])
    }

    /** Replays the state: its board, the state and the view ('H' hidden, 'F' flag, '*' mine, '0'..'8' revealed). */
    private fun replayMine(encoded: String): Triple<MineBoard, MineState, CharArray> {
        val state = decodeMine(encoded) ?: throw MineFail("ERR:INVALID_STATE")
        val board = MineBoard(state.width, state.height, state.mines, state.seed)
        val view = CharArray(state.width * state.height) { 'H' }
        if (state.operations.isNotEmpty()) {
            for (operation in state.operations.split(';')) {
                val fields = operation.split(',')
                if (fields.size != 3) throw MineFail("ERR:INVALID_STATE")
                val x = fields[1].toIntOrNull()?.takeIf { it >= 0 } ?: throw MineFail("ERR:INVALID_STATE")
                val y = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: throw MineFail("ERR:INVALID_STATE")
                if (x >= state.width || y >= state.height) throw MineFail("ERR:OUT_OF_BOUNDS")
                val index = y * state.width + x
                when (fields[0]) {
                    "R" ->
                        when (val result = board.reveal(x, y)) {
                            MineBoard.MINE -> view[index] = '*'
                            MineBoard.IGNORED -> Unit
                            else -> view[index] = '0' + result
                        }
                    "F" ->
                        when (board.toggleFlag(x, y)) {
                            Cell.Hidden -> view[index] = 'H'
                            Cell.Flagged -> view[index] = 'F'
                            Cell.Revealed -> Unit
                        }
                    else -> throw MineFail("ERR:INVALID_STATE")
                }
            }
        }
        return Triple(board, state, view)
    }

    fun minesweeperReveal(
        state: String,
        x: Int,
        y: Int,
    ): String {
        if (x < 0 || y < 0) return "ERR:OUT_OF_BOUNDS"
        return try {
            val (board, current, _) = replayMine(state)
            if (x >= current.width || y >= current.height) return "ERR:OUT_OF_BOUNDS"
            val result = board.reveal(x, y)
            val next = current.with("R,$x,$y").encode()
            when (result) {
                MineBoard.MINE -> "OK|$next|MINE"
                MineBoard.IGNORED -> "OK|$next|IGNORED"
                else -> "OK|$next|SAFE|$result"
            }
        } catch (_: MineFail) {
            "ERR:INVALID_STATE"
        }
    }

    fun minesweeperToggleFlag(
        state: String,
        x: Int,
        y: Int,
    ): String {
        if (x < 0 || y < 0) return "ERR:OUT_OF_BOUNDS"
        return try {
            val (board, current, _) = replayMine(state)
            if (x >= current.width || y >= current.height) return "ERR:OUT_OF_BOUNDS"
            val result = board.toggleFlag(x, y)
            val label =
                when (result) {
                    Cell.Hidden -> "HIDDEN"
                    Cell.Flagged -> "FLAGGED"
                    Cell.Revealed -> "REVEALED"
                }
            "OK|${current.with("F,$x,$y").encode()}|$label"
        } catch (_: MineFail) {
            "ERR:INVALID_STATE"
        }
    }

    fun minesweeperView(state: String): String =
        try {
            "OK:" + String(replayMine(state).third)
        } catch (fail: MineFail) {
            fail.code
        }

    // ---- 2048 ---------------------------------------------------------------------------------------------

    /** Slides the 4x4 [board] (16 cells, row by row) in [direction] (0 left, 1 right, 2 up, 3 down); 16 cells then the score. */
    fun game2048Slide(
        board: IntArray,
        direction: Int,
    ): LongArray {
        require(board.size == 16 && direction in 0..3 && board.none { it < 0 }) { "invalid 2048 move" }
        val cells = board.copyOf()
        var score = 0L
        for (index in 0..3) {
            val line = IntArray(4) { position ->
                val (row, col) = coords(direction, index, position)
                cells[row * 4 + col]
            }
            val merged = IntArray(4)
            val compact = line.filter { it != 0 }
            var src = 0
            var dst = 0
            while (src < compact.size) {
                if (src + 1 < compact.size && compact[src] == compact[src + 1]) {
                    val value = compact[src] * 2
                    merged[dst] = value
                    score += value
                    src += 2
                } else {
                    merged[dst] = compact[src]
                    src += 1
                }
                dst += 1
            }
            for (position in 0..3) {
                val (row, col) = coords(direction, index, position)
                cells[row * 4 + col] = merged[position]
            }
        }
        return LongArray(17) { if (it < 16) cells[it].toLong() else score }
    }

    private fun coords(
        direction: Int,
        index: Int,
        position: Int,
    ): Pair<Int, Int> =
        when (direction) {
            0 -> index to position
            1 -> index to 3 - position
            2 -> position to index
            else -> 3 - position to index
        }
}
