package com.blockpuzzle.engine

/**
 * Answers one question: can this whole tray be emptied onto this board?
 *
 * It is a search, not a heuristic. Every order of the pieces and every anchor is on the
 * table, and clears are applied after each drop — so a tray that only works because the
 * first piece completes a line and frees the room the other two need still counts as
 * solvable. That is what lets the dealer promise the player a way out.
 *
 * The search runs on a bitboard: an 8x8 playfield is exactly 64 cells, so occupancy fits
 * in a single `Long` and a whole branch costs a handful of machine words instead of an
 * array copy per node. States already seen are remembered per "which pieces are spent"
 * level, which collapses the orderings that converge on the same board.
 */
object TraySolver {

    /** One step of a witness: drop tray slot [pieceIndex] at ([row], [col]). */
    data class Placement(val pieceIndex: Int, val row: Int, val col: Int)

    /**
     * Nodes a single search may expand before giving up.
     *
     * Reaching it makes [solve] answer "no witness found", never a wrong "yes", so an
     * exhausted budget can only make the dealer re-roll — it can never let an unplayable
     * tray through. Deduplication keeps real searches orders of magnitude below this.
     */
    const val DEFAULT_NODE_BUDGET: Int = 12_000

    /** True when some order and set of anchors drops every piece in [pieces]. */
    fun canPlaceAll(
        board: Board,
        pieces: List<Piece>,
        nodeBudget: Int = DEFAULT_NODE_BUDGET,
    ): Boolean = solve(board, pieces, nodeBudget) != null

    /**
     * Finds one way to empty the tray, or null when there is none.
     *
     * The returned list is a witness in play order: applying it to [board] through the
     * normal rules drops every piece legally.
     */
    fun solve(
        board: Board,
        pieces: List<Piece>,
        nodeBudget: Int = DEFAULT_NODE_BUDGET,
    ): List<Placement>? {
        if (pieces.isEmpty()) return emptyList()
        if (board.size <= 0) return null
        return if (board.size * board.size <= Long.SIZE_BITS) {
            bitSolve(board, pieces, nodeBudget)
        } else {
            referenceSolve(board, pieces, nodeBudget)
        }
    }

    // --- bitboard search ---------------------------------------------------------------

    private fun bitSolve(board: Board, pieces: List<Piece>, nodeBudget: Int): List<Placement>? {
        val field = BitField(board.size)
        val anchors: Array<List<Anchor>> = Array(pieces.size) { field.anchorsFor(pieces[it].cells) }

        val everySpent = (1 shl pieces.size) - 1
        val seen = OccupancySet()
        var nodes = 0
        val witness = ArrayList<Placement>(pieces.size)

        fun search(occupancy: Long, spent: Int): Boolean {
            if (spent == everySpent) return true
            if (nodes++ >= nodeBudget) return false
            if (!seen.add(occupancy, spent)) return false

            for (i in pieces.indices) {
                if (spent and (1 shl i) != 0) continue
                for (anchor in anchors[i]) {
                    if (occupancy and anchor.mask != 0L) continue
                    witness += Placement(i, anchor.row, anchor.col)
                    if (search(field.settle(occupancy or anchor.mask), spent or (1 shl i))) return true
                    witness.removeAt(witness.lastIndex)
                }
            }
            return false
        }

        return if (search(field.occupancyOf(board), 0)) witness.toList() else null
    }

    // --- reference search --------------------------------------------------------------

    /**
     * The same search expressed directly over [Board].
     *
     * Handles playfields too large for a 64-bit occupancy word, and doubles as the oracle
     * the bitboard search is checked against in the tests.
     */
    internal fun referenceSolve(board: Board, pieces: List<Piece>, nodeBudget: Int): List<Placement>? {
        val everySpent = (1 shl pieces.size) - 1
        val seen = HashSet<String>()
        var nodes = 0
        val witness = ArrayList<Placement>(pieces.size)

        fun search(current: Board, spent: Int): Boolean {
            if (spent == everySpent) return true
            if (nodes++ >= nodeBudget) return false
            if (!seen.add(current.occupancyKey() + spent)) return false

            for (i in pieces.indices) {
                if (spent and (1 shl i) != 0) continue
                val piece = pieces[i]
                for (anchor in current.placements(piece.cells)) {
                    val next = current.place(piece, anchor.row, anchor.col).settled().board
                    witness += Placement(i, anchor.row, anchor.col)
                    if (search(next, spent or (1 shl i))) return true
                    witness.removeAt(witness.lastIndex)
                }
            }
            return false
        }

        return if (search(board, 0)) witness.toList() else null
    }
}

/**
 * The set of (occupancy, pieces spent) states already expanded.
 *
 * Open addressed over primitive arrays on purpose: a `HashSet<Long>` boxes every state, and
 * with tens of thousands of them per search the allocation cost dominated everything else.
 *
 * When the table fills past its load factor it simply stops remembering. That only costs
 * repeated work — the node budget still bounds the search — so a pathological position
 * degrades in speed rather than in correctness.
 */
private class OccupancySet {

    private val keys = LongArray(CAPACITY)
    private val spents = ByteArray(CAPACITY)
    private val used = BooleanArray(CAPACITY)
    private var count = 0

    /** True when the state was not already present. */
    fun add(occupancy: Long, spent: Int): Boolean {
        if (count >= LOAD_LIMIT) return true
        var i = mix(occupancy, spent)
        while (used[i]) {
            if (keys[i] == occupancy && spents[i].toInt() == spent) return false
            i = (i + 1) and MASK
        }
        used[i] = true
        keys[i] = occupancy
        spents[i] = spent.toByte()
        count++
        return true
    }

    private fun mix(occupancy: Long, spent: Int): Int {
        var z = occupancy + spent * -0x61c8864680b583ebL
        z = (z xor (z ushr 33)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 29)) * -0x6b2fb644ecceee15L
        return (z xor (z ushr 32)).toInt() and MASK
    }

    private companion object {
        const val CAPACITY = 1 shl 13
        const val MASK = CAPACITY - 1
        const val LOAD_LIMIT = (CAPACITY * 3) / 4
    }
}

/** Occupancy only, ignoring colours — two boards that differ only in colour search alike. */
private fun Board.occupancyKey(): String {
    val sb = StringBuilder(size * size + 1)
    for (r in 0 until size) {
        for (c in 0 until size) sb.append(if (isEmpty(r, c)) '.' else '#')
    }
    sb.append('|')
    return sb.toString()
}
