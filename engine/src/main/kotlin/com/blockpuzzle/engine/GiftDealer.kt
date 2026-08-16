package com.blockpuzzle.engine

import kotlin.random.Random

/**
 * Hunts for a tray picked to open the board up, rather than rolled at random.
 *
 * Used sparingly — see [GiftPolicy]. The point is that a full wipe should be something the
 * player occasionally gets a real shot at, instead of a theoretical possibility that never
 * lines up. It does not hand the win over: the tray is chosen so that a clearing line of
 * play *exists*, and finding it is still the player's job.
 *
 * The hunt is a beam search over the same bitboard the solver uses. Level by level it
 * extends the best partial lines of play with every silhouette at every anchor, keeps the
 * most promising handful, and reads the winning tray off the surviving path.
 *
 * Ranking, in order: did the board ever end up completely empty, then how many lines the
 * whole line of play wipes, then how few cells are left at the end. Emptiness comes first
 * because a wipe is the thing worth engineering; line count comes next because it is what
 * the score and the combo actually reward.
 */
internal object GiftDealer {

    /**
     * A tray worth dealing, plus what it is capable of.
     *
     * @param lowestFilled the fewest cells left on the board at any point of the line of play.
     *   Zero means the tray can take the whole board off. This, rather than the count at the
     *   end, is what the search optimises: the emptiest moment is the one the player sees.
     */
    class Gift(
        val shapes: List<Shape>,
        val linesCleared: Int,
        val lowestFilled: Int,
        val filledAfter: Int,
    ) {
        val reachedEmpty: Boolean get() = lowestFilled == 0
    }

    /** One partial line of play: a board, and the silhouette that got here from [parent]. */
    private class Step(
        val occupancy: Long,
        val parent: Int,
        val shapeIndex: Int,
        val lines: Int,
        val lowestFilled: Int,
        val jitter: Int,
    ) {
        val filled: Int = occupancy.countOneBits()
    }

    /**
     * The best tray of [count] silhouettes for [board], or null when nothing can be placed.
     *
     * @param beamWidth partial lines of play carried to the next level. Wider searches
     *   further at a proportional cost; the default is tuned to stay well inside a frame.
     */
    fun bestTray(
        board: Board,
        shapes: List<Shape>,
        count: Int,
        beamWidth: Int,
        random: Random,
    ): Gift? {
        if (count <= 0 || shapes.isEmpty() || board.size !in 1..8) return null

        val field = BitField(board.size)
        val anchors = shapes.map { field.anchorsFor(it.cells) }

        val start = field.occupancyOf(board)
        val levels = ArrayList<List<Step>>(count + 1)
        levels += listOf(Step(start, -1, -1, 0, start.countOneBits(), 0))

        for (level in 0 until count) {
            val previous = levels[level]
            val beam = Beam(beamWidth)

            for (parentIndex in previous.indices) {
                val parent = previous[parentIndex]
                for (shapeIndex in shapes.indices) {
                    // Keep the no-triplicates rule the ordinary dealer follows.
                    if (occurrences(levels, level, parentIndex, shapeIndex) >= 2) continue

                    for (anchor in anchors[shapeIndex]) {
                        if (parent.occupancy and anchor.mask != 0L) continue

                        val stamped = parent.occupancy or anchor.mask
                        val wiped = field.completedLines(stamped)
                        val settled = if (wiped > 0) field.settle(stamped) else stamped
                        val filled = settled.countOneBits()
                        val lines = parent.lines + wiped
                        val lowest = minOf(parent.lowestFilled, filled)

                        // Rank before allocating: most candidates lose to the current beam.
                        if (!beam.accepts(lowest, lines, filled)) continue
                        beam.offer(
                            Step(settled, parentIndex, shapeIndex, lines, lowest, random.nextInt(1 shl 16))
                        )
                    }
                }
            }

            val kept = beam.contents()
            if (kept.isEmpty()) return null
            levels += kept
        }

        // The beam keeps its best first, so the winning path hangs off index 0.
        val best = levels[count].first()
        val picked = ArrayList<Shape>(count)
        var cursor = 0
        var level = count
        while (level > 0) {
            val step = levels[level][cursor]
            picked += shapes[step.shapeIndex]
            cursor = step.parent
            level--
        }
        picked.reverse()

        // The tray order is cosmetic — three slots the player picks from freely — so it is
        // shuffled rather than handed over in the order the search happened to find.
        picked.shuffle(random)

        return Gift(
            shapes = picked,
            linesCleared = best.lines,
            lowestFilled = best.lowestFilled,
            filledAfter = best.filled,
        )
    }

    /**
     * Cheap test for whether taking the whole board off is even conceivable.
     *
     * Emptying the board means every occupied cell ends up inside a cleared line, so it can
     * only happen when what is left is already concentrated — all of it inside a handful of
     * rows, or a handful of columns — and those lines are close enough to complete that a
     * tray's worth of cells can finish them.
     *
     * Both are counted, because a board hugging the left edge is as sweepable as one hugging
     * the top. Runs in a single pass and rules out the vast majority of positions before the
     * beam search is even started.
     *
     * @param spanLimit how many rows (or columns) the remaining blocks may span.
     * @param holeBudget gaps a tray could plausibly fill in.
     */
    fun sweepPlausible(board: Board, spanLimit: Int, holeBudget: Int): Boolean {
        val size = board.size
        val filled = board.filledCount
        if (filled == 0) return false

        var usedRows = 0
        var usedCols = 0
        val colHasBlock = BooleanArray(size)
        for (r in 0 until size) {
            var rowHasBlock = false
            for (c in 0 until size) {
                if (!board.isEmpty(r, c)) {
                    rowHasBlock = true
                    colHasBlock[c] = true
                }
            }
            if (rowHasBlock) usedRows++
        }
        for (c in 0 until size) if (colHasBlock[c]) usedCols++

        val rowHoles = usedRows * size - filled
        val colHoles = usedCols * size - filled
        return (usedRows in 1..spanLimit && rowHoles <= holeBudget) ||
            (usedCols in 1..spanLimit && colHoles <= holeBudget)
    }

    /** How many times [shapeIndex] already appears on the path ending at ([level], [index]). */
    private fun occurrences(levels: List<List<Step>>, level: Int, index: Int, shapeIndex: Int): Int {
        var count = 0
        var currentLevel = level
        var cursor = index
        while (currentLevel > 0) {
            val step = levels[currentLevel][cursor]
            if (step.shapeIndex == shapeIndex) count++
            cursor = step.parent
            currentLevel--
        }
        return count
    }

    /**
     * The surviving partial lines of play, best first.
     *
     * Bounded and deduplicated by board, so two different orders that reach the same position
     * do not both eat a slot. Insertion into a handful of slots beats collecting tens of
     * thousands of candidates and sorting them.
     */
    private class Beam(private val capacity: Int) {

        private val items = ArrayList<Step>(capacity + 1)

        /**
         * Whether a candidate with these numbers could earn a slot, checked before allocating
         * it. Ties are let through so equally good positions still compete on their jitter.
         */
        fun accepts(lowestFilled: Int, lines: Int, filled: Int): Boolean {
            if (items.size < capacity) return true
            val worst = items[items.size - 1]
            return rank(lowestFilled, lines, filled, 0) <=
                rank(worst.lowestFilled, worst.lines, worst.filled, 0)
        }

        fun offer(step: Step) {
            val duplicate = items.indexOfFirst { it.occupancy == step.occupancy }
            if (duplicate >= 0) {
                if (isBetter(step, items[duplicate])) {
                    items.removeAt(duplicate)
                } else {
                    return
                }
            }
            var at = items.size
            while (at > 0 && isBetter(step, items[at - 1])) at--
            items.add(at, step)
            while (items.size > capacity) items.removeAt(items.size - 1)
        }

        fun contents(): List<Step> = items.toList()

        private fun isBetter(a: Step, b: Step): Boolean =
            rank(a.lowestFilled, a.lines, a.filled, a.jitter) <
                rank(b.lowestFilled, b.lines, b.filled, b.jitter)

        /** Lower is better. Packed so one comparison covers the whole ordering. */
        private fun rank(lowestFilled: Int, lines: Int, filled: Int, jitter: Int): Long {
            val emptiestRank = lowestFilled.coerceIn(0, 127).toLong()
            val lineRank = (64 - lines.coerceIn(0, 64)).toLong()
            return (emptiestRank shl 48) or (lineRank shl 40) or
                (filled.coerceIn(0, 255).toLong() shl 32) or jitter.toLong()
        }
    }
}

/**
 * When the dealer is allowed to go looking for a tailored tray.
 *
 * The numbers exist to keep it a treat rather than a crutch: rare enough that a wipe still
 * feels earned, frequent enough that it happens.
 *
 * @param minGap deals that must pass before another tailored tray is possible.
 * @param chancePercent chance per deal once the gap has passed.
 * @param rescueChancePercent chance used instead when the board is in trouble.
 * @param minFillPercent below this the board is too open for a tailored tray to mean anything.
 * @param rescueFillPercent at or above this the player is struggling and the odds improve.
 * @param minLines a tailored tray has to be able to wipe at least this many lines, or it is
 *   not worth spending the cooldown on and the deal falls through to an ordinary roll.
 * @param sweepSpanLimit rows (or columns) the remaining blocks may span for a clean sweep to
 *   be worth hunting for. The window is narrow and short-lived, so it is checked on nearly
 *   every deal instead of waiting on the cooldown — that is the only way the chance to take
 *   the whole board off ever comes up.
 * @param sweepHoleBudget gaps in those lines a single tray could plausibly fill in.
 * @param sweepMinGap deals between sweep hunts, so back-to-back sweeps are not handed over.
 * @param beamWidth partial lines of play the search carries forward.
 */
data class GiftPolicy(
    val minGap: Int = 7,
    val chancePercent: Int = 35,
    val rescueChancePercent: Int = 70,
    val minFillPercent: Int = 25,
    val rescueFillPercent: Int = 60,
    val minLines: Int = 2,
    val sweepSpanLimit: Int = 4,
    val sweepHoleBudget: Int = 16,
    val sweepMinGap: Int = 2,
    val beamWidth: Int = 20,
) {
    companion object {
        /** Never deals a tailored tray. */
        val NEVER = GiftPolicy(
            chancePercent = 0,
            rescueChancePercent = 0,
            sweepSpanLimit = 0,
        )
    }
}

/** Why the dealer is about to go looking for a tailored tray, if at all. */
internal enum class GiftAttempt {
    /** Roll normally. */
    NONE,

    /** Any tray that opens the board up will do. */
    OPENER,

    /**
     * Only a tray that takes the whole board off is worth it here. The board is dense enough
     * that a sweep is on the table, and a merely good tray would spend the cooldown for
     * something the ordinary dealer would have produced anyway.
     */
    SWEEP,
}
