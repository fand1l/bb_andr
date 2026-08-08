package com.blockpuzzle.engine

import kotlin.random.Random

/**
 * Rolls the three-piece trays.
 *
 * The dealer's promise, when [fair] is on: **a freshly dealt tray can always be emptied.**
 * Not "one of the three fits" — there is an order and a set of anchors that drops all three,
 * counting the room that opens up when a drop clears a line. So the deal never buries you;
 * losing is always the result of how you spent the tray, which is where the game actually is.
 *
 * Two ways to keep that promise, tried in order:
 *  1. **Roll and check.** Roll a tray the ordinary weighted way and ask [TraySolver] whether
 *     it can be emptied. On an open board the first roll nearly always passes, so this costs
 *     one search and leaves the piece mix completely natural.
 *  2. **Deal by simulation.** When the board is tight enough that rolls keep failing, pick the
 *     pieces one at a time against a board that is played forward as it goes: each silhouette
 *     is chosen from those that fit the board *as it will look* once the previous pieces have
 *     landed. The tray then arrives with a witness by construction, no search required.
 *
 * Step 2 cannot stall. Every silhouette pool contains the single cell, so it only runs dry on
 * a completely full board — and a settled board is never full, because filling the last free
 * cell would complete its row and clear it.
 *
 * A tray also never holds three copies of the same silhouette.
 *
 * @param uidSeed first identity handed out; bump it when restoring a save so restored pieces
 *   and newly rolled ones cannot collide.
 */
class PieceGenerator(
    private val random: Random = Random.Default,
    private val shapes: List<Shape> = Shapes.ALL,
    private val fair: Boolean = true,
    uidSeed: Int = 1,
) {

    private var nextUid: Int = uidSeed

    /** Highest uid handed out so far; persisted so restored games keep unique identities. */
    val lastUid: Int get() = nextUid - 1

    private fun weightedPick(pool: List<Shape>): Shape {
        val total = pool.sumOf { it.weight }
        if (total <= 0) return pool[random.nextInt(pool.size)]
        var ticket = random.nextInt(total)
        for (shape in pool) {
            ticket -= shape.weight
            if (ticket < 0) return shape
        }
        return pool.last()
    }

    private fun materialise(shape: Shape): Piece =
        Piece(uid = nextUid++, shape = shape, colorId = 1 + random.nextInt(PALETTE_SIZE))

    /** A single piece with no board awareness. */
    fun nextPiece(): Piece = materialise(weightedPick(shapes))

    /** Deals a tray of [count] pieces for [board]. See the class docs for the guarantee. */
    fun nextTray(board: Board, count: Int = TRAY_SIZE): List<Piece> {
        if (!fair) return rollTray(count)

        repeat(ROLL_ATTEMPTS) {
            val candidate = rollTray(count)
            if (TraySolver.canPlaceAll(board, candidate)) return candidate
        }
        return dealBySimulation(board, count)
    }

    /** An ordinary weighted roll, with the no-triplicates rule and no board awareness. */
    private fun rollTray(count: Int): List<Piece> {
        val picked = ArrayList<Shape>(count)
        repeat(count) {
            var shape = weightedPick(shapes)
            var guard = 0
            while (guard < VARIETY_RETRIES && picked.count { it.id == shape.id } >= 2) {
                shape = weightedPick(shapes)
                guard++
            }
            picked += shape
        }
        return picked.map { materialise(it) }
    }

    /**
     * Picks each piece against a board played forward, so the tray is solvable by construction.
     *
     * If the pool ever runs dry — only possible on a completely full board, which the game
     * cannot reach — the remaining slots fall back to a plain roll rather than throwing.
     */
    private fun dealBySimulation(board: Board, count: Int): List<Piece> {
        var simulated = board
        val picked = ArrayList<Shape>(count)

        while (picked.size < count) {
            val pool = shapes.filter { shape ->
                picked.count { it.id == shape.id } < 2 && simulated.hasPlacement(shape.cells)
            }
            if (pool.isEmpty()) break

            val shape = weightedPick(pool)
            val anchor = simulationAnchor(simulated, shape) ?: break
            simulated = simulated
                .place(shape.cells, anchor.row, anchor.col, SIMULATION_COLOR)
                .settled()
                .board
            picked += shape
        }

        while (picked.size < count) picked += weightedPick(shapes)
        return picked.map { materialise(it) }
    }

    /**
     * Where the simulation pretends the piece lands.
     *
     * Prefers anchors that clear lines, then ones that sit snugly against what is already
     * there. Both keep the simulated board tidy, which leaves the pieces chosen after it a
     * realistic amount of room — a witness that relies on scattering pieces across the board
     * is technically valid but useless as a hint about how the tray actually plays.
     */
    private fun simulationAnchor(board: Board, shape: Shape): CellOffset? {
        val options = board.placements(shape.cells)
        if (options.isEmpty()) return null

        var best: CellOffset? = null
        var bestScore = Int.MIN_VALUE
        for (anchor in options) {
            val stamped = board.place(shape.cells, anchor.row, anchor.col, SIMULATION_COLOR)
            val cleared = stamped.fullRows().size + stamped.fullCols().size
            val score = cleared * 1_000 + contact(board, shape, anchor) * 10 + random.nextInt(10)
            if (score > bestScore) {
                bestScore = score
                best = anchor
            }
        }
        return best
    }

    /** How many of the piece's edges would touch a wall or an already occupied cell. */
    private fun contact(board: Board, shape: Shape, anchor: CellOffset): Int {
        val own = HashSet<CellOffset>(shape.cells.size * 2)
        for (cell in shape.cells) own += CellOffset(anchor.row + cell.row, anchor.col + cell.col)

        var score = 0
        for (cell in own) {
            for (step in NEIGHBOURS) {
                val r = cell.row + step.row
                val c = cell.col + step.col
                if (!board.inBounds(r, c)) score++
                else if (CellOffset(r, c) !in own && !board.isEmpty(r, c)) score++
            }
        }
        return score
    }

    companion object {
        /** Pieces dealt at a time. */
        const val TRAY_SIZE: Int = 3

        /** Natural rolls tried before the dealer switches to simulation. */
        private const val ROLL_ATTEMPTS = 6
        private const val VARIETY_RETRIES = 8

        /** Any non-empty colour; simulated boards are never shown. */
        private const val SIMULATION_COLOR = 1

        private val NEIGHBOURS = listOf(
            CellOffset(-1, 0), CellOffset(1, 0), CellOffset(0, -1), CellOffset(0, 1),
        )
    }
}
