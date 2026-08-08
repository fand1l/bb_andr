package com.blockpuzzle.engine

import kotlin.random.Random

/**
 * Rolls the three-piece trays.
 *
 * Two rules keep the roll from feeling cheap:
 *  - **fairness** — a freshly dealt tray always contains at least one piece that fits the
 *    current board, so you can never be handed an instantly-dead hand. You still lose by
 *    painting yourself into a corner halfway through a tray, which is where the difficulty
 *    of the genre actually lives.
 *  - **variety** — a tray never contains three copies of the same silhouette.
 *
 * @param uidSeed first identity handed out; bump it when restoring a save so restored
 *   pieces and newly rolled ones cannot collide.
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

    private val totalWeight: Int = shapes.sumOf { it.weight }

    private fun rollShape(): Shape {
        var ticket = random.nextInt(totalWeight)
        for (shape in shapes) {
            ticket -= shape.weight
            if (ticket < 0) return shape
        }
        return shapes.last()
    }

    private fun materialise(shape: Shape): Piece =
        Piece(uid = nextUid++, shape = shape, colorId = 1 + random.nextInt(PALETTE_SIZE))

    /** A single piece with no board awareness. */
    fun nextPiece(): Piece = materialise(rollShape())

    /**
     * Deals a tray of [count] pieces for [board].
     *
     * When [fair] is on the deal is re-rolled until at least one piece fits. If the roller
     * cannot find such a hand within [MAX_ATTEMPTS] tries it force-substitutes a silhouette
     * that is known to fit; when literally nothing fits (a full board) it gives up and
     * returns the last roll, which the game turns into a game over.
     */
    fun nextTray(board: Board, count: Int = TRAY_SIZE): List<Piece> {
        var candidate = rollTray(count)
        if (!fair) return candidate

        var attempts = 0
        while (attempts < MAX_ATTEMPTS && candidate.none { board.hasPlacement(it) }) {
            candidate = rollTray(count)
            attempts++
        }
        if (candidate.none { board.hasPlacement(it) }) {
            val rescue = shapes.filter { board.hasPlacement(it.cells) }
            if (rescue.isNotEmpty()) {
                val slot = random.nextInt(candidate.size)
                val forced = materialise(rescue[random.nextInt(rescue.size)])
                candidate = candidate.toMutableList().also { it[slot] = forced }
            }
        }
        return candidate
    }

    private fun rollTray(count: Int): List<Piece> {
        val picked = ArrayList<Shape>(count)
        repeat(count) {
            var shape = rollShape()
            // Reject a third copy of the same silhouette; one retry is enough in practice.
            var guard = 0
            while (guard < VARIETY_RETRIES && picked.count { it.id == shape.id } >= 2) {
                shape = rollShape()
                guard++
            }
            picked += shape
        }
        return picked.map { materialise(it) }
    }

    companion object {
        /** Pieces dealt at a time. */
        const val TRAY_SIZE: Int = 3
        private const val MAX_ATTEMPTS = 60
        private const val VARIETY_RETRIES = 8
    }
}
