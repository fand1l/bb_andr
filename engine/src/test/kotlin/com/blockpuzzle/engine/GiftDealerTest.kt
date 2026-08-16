package com.blockpuzzle.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A solid [rows] x [cols] block in the top-left corner.
 *
 * Kept strictly smaller than the board in both directions so no row or column is complete:
 * these have to be positions the game could actually be sitting on, and a settled board
 * never contains a finished line.
 */
private fun blockBoard(rows: Int, cols: Int): Board {
    require(rows < BOARD_SIZE && cols < BOARD_SIZE)
    val cells = CharArray(BOARD_SIZE * BOARD_SIZE) { '0' }
    for (r in 0 until rows) for (c in 0 until cols) cells[r * BOARD_SIZE + c] = '1'
    val board = requireNotNull(Board.decode(String(cells)))
    require(board.fullRows().isEmpty() && board.fullCols().isEmpty())
    return board
}

/** Blocks strewn along the diagonal: sparse, and spanning every row and column. */
private fun diagonalBoard(): Board {
    val cells = CharArray(BOARD_SIZE * BOARD_SIZE) { '0' }
    for (i in 0 until BOARD_SIZE) cells[i * BOARD_SIZE + i] = '1'
    return requireNotNull(Board.decode(String(cells)))
}

private fun asPieces(shapes: List<Shape>): List<Piece> =
    shapes.mapIndexed { i, shape -> Piece(uid = 900 + i, shape = shape, colorId = 1 + i) }

class GiftDealerTest {

    @Test
    fun `a tailored tray can wipe a board that is one row from complete`() {
        // A solid 7x7 block. Filling the seven cells under it completes seven columns at
        // once, and since the eighth column is empty the wipe takes everything.
        val board = blockBoard(7, 7)

        val gift = assertNotNull(
            GiftDealer.bestTray(board, Shapes.ALL, 3, beamWidth = 20, random = Random(1)),
        )
        assertTrue(gift.reachedEmpty, "the search should find the full wipe")
        assertEquals(0, gift.lowestFilled)
        assertTrue(gift.linesCleared >= 7, "expected a large clear, got ${gift.linesCleared}")
        assertEquals(3, gift.shapes.size)
    }

    @Test
    fun `the emptiest moment counts, not the state after the last piece`() {
        // The wipe above happens on the second piece; the third necessarily puts cells back.
        // What the player sees is the empty board, so that is what the search optimises for.
        val gift = assertNotNull(GiftDealer.bestTray(blockBoard(7, 7), Shapes.ALL, 3, 20, Random(2)))
        assertEquals(0, gift.lowestFilled)
        assertTrue(gift.filledAfter > 0, "the third piece has to land somewhere")
    }

    @Test
    fun `a tailored tray is still a tray the player can empty`() {
        for (seed in 0 until 60) {
            val rng = Random(seed)
            var board = Board.empty()
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    if (rng.nextInt(100) < 45 + seed % 40) {
                        board = board.place(listOf(CellOffset(0, 0)), r, c, 1)
                    }
                }
            }
            board = board.settled().board
            if (board.filledCount == BOARD_SIZE * BOARD_SIZE) continue

            val gift = GiftDealer.bestTray(board, Shapes.ALL, 3, beamWidth = 20, random = rng) ?: continue
            assertEquals(3, gift.shapes.size)
            assertTrue(
                TraySolver.canPlaceAll(board, asPieces(gift.shapes)),
                "seed $seed: tailored tray ${gift.shapes.map { it.id }} cannot be emptied\n$board",
            )
        }
    }

    @Test
    fun `a tailored tray never holds three copies of one silhouette`() {
        for (seed in 0 until 60) {
            val board = blockBoard(4, 7)
            val gift = GiftDealer.bestTray(board, Shapes.ALL, 3, 20, Random(seed)) ?: continue
            val ids = gift.shapes.map { it.id }
            assertTrue(ids.toSet().size >= 2, "seed $seed dealt $ids")
        }
    }

    @Test
    fun `a full board yields nothing to tailor`() {
        val full = requireNotNull(Board.decode("1".repeat(BOARD_SIZE * BOARD_SIZE)))
        assertNull(GiftDealer.bestTray(full, Shapes.ALL, 3, 20, Random(1)))
    }

    @Test
    fun `the reported clear is what the tray actually achieves`() {
        val board = blockBoard(6, 7)
        val gift = assertNotNull(GiftDealer.bestTray(board, Shapes.ALL, 3, 20, Random(9)))

        // Replay the search's own claim through the ordinary board rules.
        val pieces = asPieces(gift.shapes)
        val witness = assertNotNull(TraySolver.solve(board, pieces))
        var current = board
        for (step in witness) {
            val piece = pieces[step.pieceIndex]
            assertTrue(current.canPlace(piece, step.row, step.col))
            current = current.place(piece, step.row, step.col).settled().board
        }
        assertTrue(gift.linesCleared > 0, "a tray worth dealing has to clear something")
    }
}

class GiftPolicyTest {

    /** Deals between consecutive tailored trays, tagged with whether the later one was a sweep. */
    private fun playAndCollectGifts(
        policy: GiftPolicy,
        seeds: IntRange,
        moveCap: Int = 400,
    ): List<Pair<Int, Boolean>> {
        val gaps = ArrayList<Pair<Int, Boolean>>()
        for (seed in seeds) {
            val rng = Random(seed)
            val game = BlockPuzzleGame(random = Random(seed), gifts = policy)
            var state = game.newGame()
            var deals = 0
            var lastGiftDeal = -1
            var guard = 0

            while (!state.isOver && guard++ < moveCap) {
                val options = state.tray.withIndex().filter { it.value != null }
                    .flatMap { (i, p) -> state.board.placements(p!!.cells).map { i to it } }
                if (options.isEmpty()) break
                // Greedy: take the drop that clears the most, which is roughly how a person plays.
                val (slot, at) = options.maxByOrNull { (i, anchor) ->
                    val piece = state.tray[i]!!
                    val after = state.board.place(piece, anchor.row, anchor.col)
                    after.fullRows().size + after.fullCols().size
                } ?: options[rng.nextInt(options.size)]

                val move = game.place(slot, at.row, at.col) ?: break
                state = game.state
                if (move.trayRefilled) {
                    deals++
                    if (game.lastDealWasGift) {
                        if (lastGiftDeal >= 0) gaps += (deals - lastGiftDeal) to game.lastGiftSweeps
                        lastGiftDeal = deals
                    }
                }
            }
        }
        return gaps
    }

    @Test
    fun `tailored trays keep their distance`() {
        val policy = GiftPolicy()
        val gaps = playAndCollectGifts(policy, 0 until 40)
        assertTrue(gaps.isNotEmpty(), "no tailored trays were dealt at all")

        val (sweeps, openers) = gaps.partition { it.second }
        assertTrue(
            openers.all { it.first >= policy.minGap },
            "ordinary tailored trays came too close: ${openers.filter { it.first < policy.minGap }}",
        )
        // Sweeps run on their own, much shorter cooldown: the position that makes one possible
        // is fleeting, so waiting out the long gap would mean never seeing one.
        assertTrue(
            sweeps.all { it.first >= policy.sweepMinGap },
            "sweeps came too close: ${sweeps.filter { it.first < policy.sweepMinGap }}",
        )
        assertTrue(sweeps.isNotEmpty(), "no board ever became sweepable across 40 runs")
    }

    @Test
    fun `the policy can be switched off entirely`() {
        val gen = PieceGenerator(random = Random(3), gifts = GiftPolicy.NEVER)
        val board = blockBoard(5, 7)
        repeat(60) {
            gen.nextTray(board)
            assertFalse(gen.lastDealWasGift, "a disabled policy must never tailor a tray")
        }
        assertTrue(board.filledCount > 0)
    }

    @Test
    fun `an open board is left alone`() {
        // Sparse and spread across every row and column: nothing to rescue, nothing to sweep.
        val gen = PieceGenerator(random = Random(5), gifts = GiftPolicy(minGap = 0, sweepMinGap = 0))
        val open = diagonalBoard()
        repeat(80) {
            gen.nextTray(open)
            assertFalse(gen.lastDealWasGift, "tailored a tray for an almost empty board")
        }
    }

    @Test
    fun `the sweep check only fires where a sweep is conceivable`() {
        val policy = GiftPolicy()
        // Concentrated in three rows with few gaps: a tray really could take it all off.
        assertTrue(GiftDealer.sweepPlausible(blockBoard(3, 7), policy.sweepSpanLimit, policy.sweepHoleBudget))
        // Spread over six rows and seven columns: far more than one tray could finish.
        assertFalse(GiftDealer.sweepPlausible(blockBoard(6, 7), policy.sweepSpanLimit, policy.sweepHoleBudget))
        // Sparse but touching every row and column.
        assertFalse(GiftDealer.sweepPlausible(diagonalBoard(), policy.sweepSpanLimit, policy.sweepHoleBudget))
        // Nothing on the board is not a sweep opportunity, it is just an empty board.
        assertFalse(GiftDealer.sweepPlausible(Board.empty(), policy.sweepSpanLimit, policy.sweepHoleBudget))
    }

    @Test
    fun `a column-hugging board is recognised as sweepable too`() {
        // The same shape rotated: three columns rather than three rows.
        val cells = CharArray(BOARD_SIZE * BOARD_SIZE) { '0' }
        for (r in 0 until BOARD_SIZE - 1) for (c in 0 until 3) cells[r * BOARD_SIZE + c] = '1'
        val board = requireNotNull(Board.decode(String(cells)))
        val policy = GiftPolicy()
        assertTrue(GiftDealer.sweepPlausible(board, policy.sweepSpanLimit, policy.sweepHoleBudget))
    }

    @Test
    fun `the cooldown survives save and restore`() {
        val game = BlockPuzzleGame(random = Random(17))
        game.newGame()
        repeat(20) {
            val slot = (0 until PieceGenerator.TRAY_SIZE).firstOrNull { game.state.tray[it] != null }
                ?: return@repeat
            val spot = game.hint(slot) ?: return@repeat
            game.place(slot, spot.row, spot.col)
        }
        val before = game.state
        assertTrue(before.dealsSinceGift > 0, "the cooldown should have advanced")

        val restored = requireNotNull(GameSaver.decode(GameSaver.encode(before, game.nextUid())))
        assertEquals(before.dealsSinceGift, restored.state.dealsSinceGift)

        val revived = BlockPuzzleGame(random = Random(17))
        revived.restore(restored.state, restored.nextUid)
        assertEquals(before.dealsSinceGift, revived.state.dealsSinceGift, "restarting must not farm gifts")
    }

    @Test
    fun `version 1 saves still load`() {
        val legacy = "1|" + "0".repeat(BOARD_SIZE * BOARD_SIZE) +
            "|120|300|2|4|9|11|0|42|7:dot:3,-,9:sq2:5"
        val restored = assertNotNull(GameSaver.decode(legacy), "an older save must still resume")
        assertEquals(120, restored.state.score)
        assertEquals(300, restored.state.best)
        assertEquals(42, restored.nextUid)
        assertEquals(0, restored.state.dealsSinceGift, "the missing field defaults to no cooldown")
        assertEquals("dot", restored.state.tray[0]?.shape?.id)
        assertNull(restored.state.tray[1])
        assertEquals("sq2", restored.state.tray[2]?.shape?.id)
    }
}
