package com.blockpuzzle.engine

/** Side of the playfield. The genre standard is 8x8. */
const val BOARD_SIZE: Int = 8

/**
 * Immutable playfield.
 *
 * Every cell holds either [EMPTY] or a 1-based palette colour id. Mutating operations
 * return a new [Board], which keeps the whole game state trivially snapshot-able for
 * Compose and for undo-free save/restore.
 */
class Board private constructor(
    val size: Int,
    private val cells: IntArray,
) {

    companion object {
        const val EMPTY: Int = 0

        fun empty(size: Int = BOARD_SIZE): Board = Board(size, IntArray(size * size))

        /**
         * Rebuilds a board from [encode]. Returns null when the payload is not a
         * square grid of digits, so a corrupted save degrades to "start a new game".
         */
        fun decode(text: String): Board? {
            val side = Math.sqrt(text.length.toDouble()).toInt()
            if (side <= 0 || side * side != text.length) return null
            val data = IntArray(text.length)
            for (i in text.indices) {
                val d = text[i] - '0'
                if (d < 0 || d > PALETTE_SIZE) return null
                data[i] = d
            }
            return Board(side, data)
        }
    }

    private fun index(row: Int, col: Int) = row * size + col

    operator fun get(row: Int, col: Int): Int = cells[index(row, col)]

    fun inBounds(row: Int, col: Int): Boolean = row in 0 until size && col in 0 until size

    fun isEmpty(row: Int, col: Int): Boolean = get(row, col) == EMPTY

    val filledCount: Int get() = cells.count { it != EMPTY }

    val isClear: Boolean get() = cells.all { it == EMPTY }

    /** True when every cell of [shapeCells], anchored at ([row], [col]), is inside the board and free. */
    fun canPlace(shapeCells: List<CellOffset>, row: Int, col: Int): Boolean {
        for (cell in shapeCells) {
            val r = row + cell.row
            val c = col + cell.col
            if (!inBounds(r, c)) return false
            if (cells[index(r, c)] != EMPTY) return false
        }
        return true
    }

    fun canPlace(piece: Piece, row: Int, col: Int): Boolean = canPlace(piece.cells, row, col)

    /** True when [shapeCells] fits somewhere — the primitive behind game-over detection. */
    fun hasPlacement(shapeCells: List<CellOffset>): Boolean {
        val h = shapeCells.maxOf { it.row } + 1
        val w = shapeCells.maxOf { it.col } + 1
        if (h > size || w > size) return false
        for (row in 0..size - h) {
            for (col in 0..size - w) {
                if (canPlace(shapeCells, row, col)) return true
            }
        }
        return false
    }

    fun hasPlacement(piece: Piece): Boolean = hasPlacement(piece.cells)

    /** All anchors where [shapeCells] fits. Used by tests and by the "is this tray dead" check. */
    fun placements(shapeCells: List<CellOffset>): List<CellOffset> {
        val h = shapeCells.maxOf { it.row } + 1
        val w = shapeCells.maxOf { it.col } + 1
        if (h > size || w > size) return emptyList()
        val out = ArrayList<CellOffset>()
        for (row in 0..size - h) {
            for (col in 0..size - w) {
                if (canPlace(shapeCells, row, col)) out += CellOffset(row, col)
            }
        }
        return out
    }

    /**
     * Stamps [piece] at ([row], [col]).
     *
     * @throws IllegalArgumentException when the placement is illegal — callers are
     *   expected to have asked [canPlace] first.
     */
    fun place(piece: Piece, row: Int, col: Int): Board {
        require(canPlace(piece, row, col)) { "Illegal placement of ${piece.shape.id} at ($row,$col)" }
        val next = cells.copyOf()
        for (cell in piece.cells) {
            next[index(row + cell.row, col + cell.col)] = piece.colorId
        }
        return Board(size, next)
    }

    fun fullRows(): List<Int> = (0 until size).filter { r ->
        (0 until size).all { c -> cells[index(r, c)] != EMPTY }
    }

    fun fullCols(): List<Int> = (0 until size).filter { c ->
        (0 until size).all { r -> cells[index(r, c)] != EMPTY }
    }

    /**
     * Removes [rows] and [cols] in one pass.
     *
     * Rows and columns are resolved against the *same* pre-clear snapshot, so a cell
     * sitting on the intersection of a full row and a full column is only counted once —
     * that is what makes a simultaneous row+column clear worth exactly the cells it wipes.
     */
    fun clearLines(rows: List<Int>, cols: List<Int>): ClearResult {
        if (rows.isEmpty() && cols.isEmpty()) return ClearResult(this, emptyList())
        val next = cells.copyOf()
        val wiped = LinkedHashSet<CellOffset>()
        for (r in rows) {
            for (c in 0 until size) {
                wiped += CellOffset(r, c)
                next[index(r, c)] = EMPTY
            }
        }
        for (c in cols) {
            for (r in 0 until size) {
                wiped += CellOffset(r, c)
                next[index(r, c)] = EMPTY
            }
        }
        return ClearResult(Board(size, next), wiped.toList())
    }

    /** Compact save format: one digit per cell, row-major. */
    fun encode(): String {
        val sb = StringBuilder(cells.size)
        for (v in cells) sb.append(('0' + v))
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean =
        other is Board && other.size == size && other.cells.contentEquals(cells)

    override fun hashCode(): Int = 31 * size + cells.contentHashCode()

    override fun toString(): String = buildString {
        for (r in 0 until size) {
            for (c in 0 until size) append(if (isEmpty(r, c)) '.' else '#')
            append('\n')
        }
    }
}

/** Board after a clear plus the absolute coordinates that were wiped (for the pop animation). */
data class ClearResult(
    val board: Board,
    val clearedCells: List<CellOffset>,
)
