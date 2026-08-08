package com.blockpuzzle.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreRulesTest {

    private val rules = ScoreRules()

    @Test
    fun `placement pays per cell`() {
        assertEquals(1, rules.placementScore(1))
        assertEquals(9, rules.placementScore(9))
    }

    @Test
    fun `clear bonus grows triangularly with simultaneous lines`() {
        assertEquals(10, rules.clearScore(1, 1))
        assertEquals(30, rules.clearScore(2, 1))
        assertEquals(60, rules.clearScore(3, 1))
        assertEquals(100, rules.clearScore(4, 1))
    }

    @Test
    fun `combo scales the clear bonus and caps out`() {
        assertEquals(10, rules.clearScore(1, 1))
        assertEquals(15, rules.clearScore(1, 2))
        assertEquals(20, rules.clearScore(1, 3))
        val capped = rules.clearScore(1, rules.maxComboLevel)
        assertEquals(capped, rules.clearScore(1, rules.maxComboLevel + 50), "multiplier must plateau")
    }

    @Test
    fun `no lines is worth nothing at any combo`() {
        assertEquals(0, rules.clearScore(0, 1))
        assertEquals(0, rules.clearScore(0, 9))
        assertEquals(0, rules.clearScore(-1, 3))
    }
}

class GameFlowTest {

    private fun game(seed: Int = 7) = BlockPuzzleGame(random = Random(seed))

    @Test
    fun `a new game deals three usable pieces on an empty board`() {
        val g = game()
        val s = g.newGame()
        assertEquals(PieceGenerator.TRAY_SIZE, s.tray.size)
        assertEquals(PieceGenerator.TRAY_SIZE, s.trayPieces.size)
        assertEquals(0, s.score)
        assertFalse(s.isOver)
        assertTrue(s.board.isClear)
        assertEquals(s.trayPieces.map { it.uid }.toSet().size, s.trayPieces.size, "uids must be unique")
    }

    @Test
    fun `an illegal drop changes nothing`() {
        val g = game()
        val before = g.newGame()
        assertNull(g.place(0, -1, 0))
        assertNull(g.place(0, 99, 99))
        assertNull(g.place(9, 0, 0), "no such tray slot")
        assertEquals(before, g.state)
    }

    @Test
    fun `dropping a piece consumes its slot and scores its cells`() {
        val g = game()
        val start = g.newGame()
        val piece = requireNotNull(start.tray[0])
        val spot = requireNotNull(g.hint(0))
        val move = assertNotNull(g.place(0, spot.row, spot.col))

        assertEquals(piece.size, move.placementScore)
        assertEquals(piece.size, move.placedCells.size)
        assertEquals(piece.colorId, move.placedColorId)
        val after = g.state
        assertNull(after.tray[0])
        assertEquals(1, after.piecesPlaced)
        assertTrue(after.score >= piece.size)
    }

    @Test
    fun `the tray refills only once all three slots are spent`() {
        val g = game()
        g.newGame()
        val firstUids = g.state.trayPieces.map { it.uid }.toSet()

        var dropped = 0
        while (dropped < PieceGenerator.TRAY_SIZE) {
            val slot = (0 until PieceGenerator.TRAY_SIZE).first { g.state.tray[it] != null }
            val spot = requireNotNull(g.hint(slot))
            val move = assertNotNull(g.place(slot, spot.row, spot.col))
            dropped++
            if (dropped < PieceGenerator.TRAY_SIZE) {
                assertFalse(move.trayRefilled, "refilled early after $dropped drops")
                assertEquals(PieceGenerator.TRAY_SIZE - dropped, g.state.trayPieces.size)
            } else {
                assertTrue(move.trayRefilled, "should refill once empty")
            }
        }
        val secondUids = g.state.trayPieces.map { it.uid }.toSet()
        assertEquals(PieceGenerator.TRAY_SIZE, secondUids.size)
        assertTrue(firstUids.intersect(secondUids).isEmpty(), "fresh pieces must have fresh uids")
    }

    @Test
    fun `completing a row wipes it and pays the line bonus`() {
        val g = BlockPuzzleGame(random = Random(1))
        g.newGame()
        // Rebuild a deterministic near-complete row through restore().
        val board = requireNotNull(
            Board.decode(
                buildString {
                    // row 0 filled except the last cell, everything else empty
                    repeat(7) { append('1') }
                    append('0')
                    append("0".repeat(56))
                }
            )
        )
        val dot = Piece(uid = 500, shape = requireNotNull(Shapes.byId("dot")), colorId = 2)
        g.restore(
            GameState(
                board = board,
                tray = listOf(dot, null, null),
                score = 0, best = 0, combo = 0, bestCombo = 0,
                linesCleared = 0, piecesPlaced = 0, isOver = false,
            ),
            nextUid = 501,
        )

        val move = assertNotNull(g.place(0, 0, 7))
        assertEquals(listOf(0), move.clearedRows)
        assertTrue(move.clearedCols.isEmpty())
        assertEquals(8, move.clearedCells.size)
        assertEquals(1, move.combo)
        assertEquals(10, move.clearScore)
        assertEquals(1, move.placementScore)
        assertTrue(move.isAllClear, "board is empty afterwards")
        assertEquals(1 + 10 + ScoreRules().allClearBonus, move.totalScore)
        assertTrue(g.state.board.isClear)
        assertEquals(1, g.state.linesCleared)
    }

    @Test
    fun `a dry drop breaks the combo`() {
        val g = BlockPuzzleGame(random = Random(3))
        g.newGame()
        val dotA = Piece(1000, requireNotNull(Shapes.byId("dot")), 2)
        val dotB = Piece(1001, requireNotNull(Shapes.byId("dot")), 3)
        val dotC = Piece(1002, requireNotNull(Shapes.byId("dot")), 4)
        // Rows 0 and 1 each missing their last cell; row 5 completely empty.
        val board = requireNotNull(
            Board.decode(
                buildString {
                    repeat(7) { append('1') }; append('0')
                    repeat(7) { append('1') }; append('0')
                    append("0".repeat(48))
                }
            )
        )
        g.restore(
            GameState(board, listOf(dotA, dotB, dotC), 0, 0, 0, 0, 0, 0, false),
            nextUid = 1003,
        )

        val first = assertNotNull(g.place(0, 0, 7))
        assertEquals(1, first.combo)
        val second = assertNotNull(g.place(1, 1, 7))
        assertEquals(2, second.combo, "consecutive clears raise the combo")
        assertEquals(15, second.clearScore, "combo 2 pays 150 percent")

        val third = assertNotNull(g.place(2, 5, 5))
        assertEquals(0, third.combo, "a drop with no clear resets the streak")
        assertEquals(0, third.clearScore)
        assertEquals(0, g.state.combo)
        assertEquals(2, g.state.bestCombo)
    }

    @Test
    fun `simultaneous row and column clear scores as two lines`() {
        val g = BlockPuzzleGame(random = Random(5))
        g.newGame()
        // Row 0 and column 7 both missing only cell (0,7).
        val cells = CharArray(64) { '0' }
        for (c in 0 until 7) cells[c] = '1'
        for (r in 1 until 8) cells[r * 8 + 7] = '1'
        val board = requireNotNull(Board.decode(String(cells)))
        val dot = Piece(2000, requireNotNull(Shapes.byId("dot")), 5)
        g.restore(GameState(board, listOf(dot, null, null), 0, 0, 0, 0, 0, 0, false), nextUid = 2001)

        val move = assertNotNull(g.place(0, 0, 7))
        assertEquals(listOf(0), move.clearedRows)
        assertEquals(listOf(7), move.clearedCols)
        assertEquals(2, move.linesCleared)
        assertEquals(15, move.clearedCells.size)
        assertEquals(30, move.clearScore, "two lines at combo 1")
        assertTrue(g.state.board.isClear)
    }

    @Test
    fun `game ends when no remaining piece fits`() {
        val g = BlockPuzzleGame(random = Random(11))
        g.newGame()
        // Board full except a single cell; the tray holds only a 5-long bar.
        val cells = CharArray(64) { '1' }
        cells[63] = '0'
        val board = requireNotNull(Board.decode(String(cells)))
        val bar = Piece(3000, requireNotNull(Shapes.byId("h5")), 1)
        g.restore(GameState(board, listOf(bar, null, null), 0, 0, 0, 0, 0, 0, false), nextUid = 3001)

        assertTrue(g.state.isDead())
        assertTrue(g.state.isOver)
        assertNull(g.place(0, 0, 0), "no drops are accepted after the game is over")
    }

    @Test
    fun `personal best survives a new game`() {
        val g = game()
        g.newGame()
        val spot = requireNotNull(g.hint(0))
        g.place(0, spot.row, spot.col)
        val earned = g.state.score
        assertTrue(earned > 0)
        assertEquals(earned, g.state.best)

        g.newGame()
        assertEquals(0, g.state.score)
        assertEquals(earned, g.state.best, "best score carries over")
    }

    @Test
    fun `seedBest only ever raises the stored best`() {
        val g = game()
        g.newGame()
        g.seedBest(4321)
        assertEquals(4321, g.state.best)
        g.seedBest(10)
        assertEquals(4321, g.state.best)
    }

    @Test
    fun `clearBest forgets the record but never drops below the live score`() {
        val g = game()
        g.newGame()
        g.seedBest(9999)
        val spot = requireNotNull(g.hint(0))
        g.place(0, spot.row, spot.col)
        val earned = g.state.score
        assertEquals(9999, g.state.best)

        g.clearBest()
        assertEquals(earned, g.state.best, "the run in progress is the new floor")
        assertEquals(earned, g.state.score, "clearing the record must not touch the score")

        val fresh = game()
        fresh.newGame()
        fresh.clearBest()
        assertEquals(0, fresh.state.best)
    }

    @Test
    fun `hint prefers an anchor that completes a line`() {
        val g = BlockPuzzleGame(random = Random(13))
        g.newGame()
        val cells = CharArray(64) { '0' }
        for (c in 0 until 7) cells[c] = '1' // row 0 missing (0,7)
        val board = requireNotNull(Board.decode(String(cells)))
        val dot = Piece(4000, requireNotNull(Shapes.byId("dot")), 1)
        g.restore(GameState(board, listOf(dot, null, null), 0, 0, 0, 0, 0, 0, false), nextUid = 4001)

        assertEquals(CellOffset(0, 7), g.hint(0))
    }
}

class GeneratorFairnessTest {

    @Test
    fun `a fresh tray always contains a placeable piece on a cramped board`() {
        // Board leaves exactly one free 2x2 pocket, so most silhouettes will not fit.
        val cells = CharArray(64) { '1' }
        for (r in 6 until 8) for (c in 6 until 8) cells[r * 8 + c] = '0'
        val cramped = requireNotNull(Board.decode(String(cells)))

        repeat(200) { seed ->
            val gen = PieceGenerator(random = Random(seed))
            val tray = gen.nextTray(cramped)
            assertEquals(PieceGenerator.TRAY_SIZE, tray.size)
            assertTrue(
                tray.any { cramped.hasPlacement(it) },
                "seed $seed dealt a dead tray: ${tray.map { it.shape.id }}",
            )
        }
    }

    @Test
    fun `a tray never holds three copies of one silhouette`() {
        val empty = Board.empty()
        repeat(500) { seed ->
            val tray = PieceGenerator(random = Random(seed)).nextTray(empty)
            val ids = tray.map { it.shape.id }
            assertTrue(ids.toSet().size >= 2, "seed $seed dealt $ids")
        }
    }

    @Test
    fun `unfair generation is allowed to deal a dead tray`() {
        val cells = CharArray(64) { '1' }
        cells[0] = '0'
        val nearlyFull = requireNotNull(Board.decode(String(cells)))
        val gen = PieceGenerator(random = Random(42), fair = false)
        val anyDead = (0 until 50).any { gen.nextTray(nearlyFull).none { p -> nearlyFull.hasPlacement(p) } }
        assertTrue(anyDead, "with fairness off a dead tray must be reachable")
    }

    @Test
    fun `uids are handed out without repeats`() {
        val gen = PieceGenerator(random = Random(99))
        val board = Board.empty()
        val uids = (0 until 40).flatMap { gen.nextTray(board).map { p -> p.uid } }
        assertEquals(uids.size, uids.toSet().size)
        assertEquals(uids.max(), gen.lastUid)
    }
}

class SaveRestoreTest {

    @Test
    fun `a live game round trips through the save format`() {
        val g = BlockPuzzleGame(random = Random(21))
        g.newGame()
        repeat(5) {
            val slot = (0 until PieceGenerator.TRAY_SIZE).firstOrNull { g.state.tray[it] != null } ?: return@repeat
            val spot = g.hint(slot) ?: return@repeat
            g.place(slot, spot.row, spot.col)
        }
        val original = g.state
        val text = GameSaver.encode(original, g.nextUid())

        val restored = requireNotNull(GameSaver.decode(text))
        assertEquals(original, restored.state)
        assertEquals(g.nextUid(), restored.nextUid)

        val revived = BlockPuzzleGame(random = Random(21))
        revived.restore(restored.state, restored.nextUid)
        assertEquals(original.board, revived.state.board)
        assertEquals(original.score, revived.state.score)
        assertEquals(original.tray.map { it?.uid }, revived.state.tray.map { it?.uid })
    }

    @Test
    fun `partially spent trays round trip`() {
        val g = BlockPuzzleGame(random = Random(31))
        g.newGame()
        val spot = requireNotNull(g.hint(1))
        g.place(1, spot.row, spot.col)
        val text = GameSaver.encode(g.state, g.nextUid())
        val restored = requireNotNull(GameSaver.decode(text))
        assertNull(restored.state.tray[1])
        assertEquals(g.state, restored.state)
    }

    @Test
    fun `corrupt saves are rejected rather than crashing`() {
        assertNull(GameSaver.decode(null))
        assertNull(GameSaver.decode(""))
        assertNull(GameSaver.decode("garbage"))
        assertNull(GameSaver.decode("2|" + "0".repeat(64) + "|0|0|0|0|0|0|0|1|-,-,-"), "wrong version")
        assertNull(GameSaver.decode("1|" + "0".repeat(64) + "|0|0|0|0|0|0|0|1|-,-"), "wrong tray size")
        assertNull(GameSaver.decode("1|" + "0".repeat(64) + "|0|0|0|0|0|0|0|1|1:nope:2,-,-"), "unknown shape")
        assertNull(GameSaver.decode("1|" + "0".repeat(64) + "|x|0|0|0|0|0|0|1|-,-,-"), "bad score")
        assertNull(GameSaver.decode("1|" + "0".repeat(64) + "|0|0|0|0|0|0|0|1|1:dot:99,-,-"), "colour out of palette")
    }
}

class RandomPlaythroughTest {

    /**
     * Plays many full games with random legal drops and asserts the invariants that must
     * hold on every single turn. This is the net that catches rule regressions the
     * hand-written cases would miss.
     */
    @Test
    fun `random playthroughs keep every invariant`() {
        var totalMoves = 0
        var gamesEndedNaturally = 0

        for (seed in 0 until 120) {
            val rng = Random(seed)
            val g = BlockPuzzleGame(random = rng)
            var state = g.newGame()
            var previousScore = 0
            var guard = 0

            while (!state.isOver && guard < 5_000) {
                guard++
                val options = state.tray.withIndex()
                    .filter { (_, p) -> p != null }
                    .flatMap { (i, p) -> g.state.board.placements(p!!.cells).map { i to it } }
                assertTrue(options.isNotEmpty(), "seed $seed: not over but nothing fits")

                val (slot, anchor) = options[rng.nextInt(options.size)]
                val piece = requireNotNull(state.tray[slot])
                val filledBefore = state.board.filledCount

                val move = assertNotNull(g.place(slot, anchor.row, anchor.col), "seed $seed: legal drop rejected")
                totalMoves++
                state = g.state

                assertTrue(move.totalScore >= previousScore, "seed $seed: score went backwards")
                previousScore = move.totalScore
                assertEquals(move.totalScore, state.score)
                assertTrue(state.score <= state.best)

                assertEquals(piece.size, move.placedCells.size)
                assertEquals(move.placedCells.size, move.placedCells.toSet().size)
                assertEquals(move.clearedCells.size, move.clearedCells.toSet().size)
                for (cell in move.placedCells) {
                    assertTrue(cell.row in 0 until BOARD_SIZE && cell.col in 0 until BOARD_SIZE)
                }

                val expectedFilled = filledBefore + piece.size - move.clearedCells.size
                assertEquals(expectedFilled, state.board.filledCount, "seed $seed: cell accounting is off")

                assertTrue(state.board.fullRows().isEmpty(), "seed $seed: a full row survived the clear")
                assertTrue(state.board.fullCols().isEmpty(), "seed $seed: a full column survived the clear")

                assertEquals(if (move.linesCleared > 0) move.combo else 0, state.combo)
                assertTrue(state.combo <= state.bestCombo)
                assertEquals(PieceGenerator.TRAY_SIZE, state.tray.size)
                assertTrue(state.trayPieces.map { it.uid }.toSet().size == state.trayPieces.size)
                assertEquals(state.isDead(), state.isOver)
            }

            assertTrue(guard < 5_000, "seed $seed: game never terminated")
            if (state.isOver) gamesEndedNaturally++
            assertTrue(state.trayPieces.none { state.board.hasPlacement(it) }, "seed $seed: ended while a piece fit")
        }

        assertEquals(120, gamesEndedNaturally)
        assertTrue(totalMoves > 1_000, "expected a meaningful sample, played $totalMoves moves")
    }

    @Test
    fun `saving and restoring mid game never changes the outcome of the next move`() {
        for (seed in 0 until 40) {
            val rng = Random(seed)
            val g = BlockPuzzleGame(random = rng)
            g.newGame()
            repeat(12) {
                val slot = (0 until PieceGenerator.TRAY_SIZE).firstOrNull { g.state.tray[it] != null } ?: return@repeat
                val spot = g.hint(slot) ?: return@repeat
                g.place(slot, spot.row, spot.col)
            }
            val snapshot = g.state
            val revived = BlockPuzzleGame(random = Random(seed))
            val decoded = requireNotNull(GameSaver.decode(GameSaver.encode(snapshot, g.nextUid())))
            revived.restore(decoded.state, decoded.nextUid)

            val slot = (0 until PieceGenerator.TRAY_SIZE).firstOrNull { snapshot.tray[it] != null }
            if (slot != null) {
                val spot = g.hint(slot)
                if (spot != null) {
                    val a = g.place(slot, spot.row, spot.col)
                    val b = revived.place(slot, spot.row, spot.col)
                    assertEquals(a?.totalScore, b?.totalScore, "seed $seed diverged after restore")
                    assertEquals(g.state.board, revived.state.board)
                }
            }
        }
    }
}
