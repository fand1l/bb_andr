package com.blockpuzzle.engine

/**
 * The playfield packed into a single machine word.
 *
 * An 8x8 board is exactly 64 cells, so occupancy fits in one `Long` and a whole
 * hypothetical line of play costs a handful of ANDs instead of an array copy per step.
 * Cell (row, col) lives at bit `row * size + col`.
 *
 * Colours are deliberately absent: everything that searches ahead — [TraySolver] deciding
 * whether a tray can be emptied, [GiftDealer] hunting for a tray that opens the board — only
 * ever cares which cells are taken.
 */
internal class BitField(val size: Int) {

    init {
        require(size in 1..8) { "a $size x$size board does not fit in a 64-bit word" }
    }

    val rowMasks: LongArray = LongArray(size) { r -> ((1L shl size) - 1L) shl (r * size) }

    val colMasks: LongArray = LongArray(size) { c ->
        var mask = 0L
        for (r in 0 until size) mask = mask or (1L shl (r * size + c))
        mask
    }

    /** Which cells of [board] are taken. */
    fun occupancyOf(board: Board): Long {
        var occupancy = 0L
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!board.isEmpty(r, c)) occupancy = occupancy or (1L shl (r * size + c))
            }
        }
        return occupancy
    }

    /**
     * Every legal anchor for [shapeCells], with the cells it would take already packed.
     *
     * Because the anchor loop stops before the silhouette would overhang, each anchor is a
     * plain shift of the mask at the origin — no cell can wrap into the next row.
     */
    fun anchorsFor(shapeCells: List<CellOffset>): List<Anchor> {
        val height = shapeCells.maxOf { it.row } + 1
        val width = shapeCells.maxOf { it.col } + 1
        if (height > size || width > size) return emptyList()

        var base = 0L
        for (cell in shapeCells) base = base or (1L shl (cell.row * size + cell.col))

        val out = ArrayList<Anchor>((size - height + 1) * (size - width + 1))
        for (row in 0..size - height) {
            for (col in 0..size - width) {
                out += Anchor(base shl (row * size + col), row, col)
            }
        }
        return out
    }

    /** How many rows and columns are complete in [occupancy]. */
    fun completedLines(occupancy: Long): Int {
        var lines = 0
        for (r in 0 until size) if (occupancy and rowMasks[r] == rowMasks[r]) lines++
        for (c in 0 until size) if (occupancy and colMasks[c] == colMasks[c]) lines++
        return lines
    }

    /**
     * Wipes every complete row and column at once.
     *
     * Both are resolved against the same pre-clear word, which is what makes a cell on the
     * intersection of a full row and a full column disappear once rather than twice.
     */
    fun settle(occupancy: Long): Long {
        var clear = 0L
        for (r in 0 until size) if (occupancy and rowMasks[r] == rowMasks[r]) clear = clear or rowMasks[r]
        for (c in 0 until size) if (occupancy and colMasks[c] == colMasks[c]) clear = clear or colMasks[c]
        return occupancy and clear.inv()
    }
}

/** A place a silhouette can sit, with its cells packed into a mask. */
internal class Anchor(val mask: Long, val row: Int, val col: Int)
