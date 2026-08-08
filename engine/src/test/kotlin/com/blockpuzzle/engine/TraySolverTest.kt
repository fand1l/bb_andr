package com.blockpuzzle.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun shape(id: String) = requireNotNull(Shapes.byId(id)) { "unknown shape $id" }

private fun tray(vararg ids: String): List<Piece> =
    ids.mapIndexed { i, id -> Piece(uid = 100 + i, shape = shape(id), colorId = 1 + i % PALETTE_SIZE) }

/** A board with [filled] rows completely full, counting from the top. */
private fun boardWithFullRows(filled: Int): Board {
    val cells = CharArray(BOARD_SIZE * BOARD_SIZE) { '0' }
    for (r in 0 until filled) for (c in 0 until BOARD_SIZE) cells[r * BOARD_SIZE + c] = '1'
    return requireNotNull(Board.decode(String(cells)))
}

private fun randomBoard(rng: Random, fill: Double): Board {
    var board = Board.empty()
    for (r in 0 until BOARD_SIZE) {
        for (c in 0 until BOARD_SIZE) {
            if (rng.nextDouble() < fill) {
                board = board.place(listOf(CellOffset(0, 0)), r, c, 1 + rng.nextInt(PALETTE_SIZE))
            }
        }
    }
    // Settle so the fixture is a board the game could actually be sitting on.
    return board.settled().board
}

/** Replays a witness through the plain board rules and reports whether every step was legal. */
private fun replay(board: Board, pieces: List<Piece>, witness: List<TraySolver.Placement>): Boolean {
    var current = board
    val spent = HashSet<Int>()
    for (step in witness) {
        if (!spent.add(step.pieceIndex)) return false
        val piece = pieces.getOrNull(step.pieceIndex) ?: return false
        if (!current.canPlace(piece, step.row, step.col)) return false
        current = current.place(piece, step.row, step.col).settled().board
    }
    return spent.size == pieces.size
}

class TraySolverTest {

    @Test
    fun `a tray that only fits because a clear frees the room is solvable`() {
        // Seven full rows and one empty one: eight free cells, against three four-cell bars.
        val board = boardWithFullRows(7)
        val pieces = tray("h4", "h4", "h4")

        val freeCells = BOARD_SIZE * BOARD_SIZE - board.filledCount
        val pieceCells = pieces.sumOf { it.size }
        assertEquals(8, freeCells)
        assertEquals(12, pieceCells)
        assertTrue(pieceCells > freeCells, "the tray cannot fit without a clear opening space")

        val witness = assertNotNull(
            TraySolver.solve(board, pieces),
            "the solver must find the line-clearing line of play",
        )
        assertEquals(3, witness.size)
        assertTrue(replay(board, pieces, witness), "the witness must survive the real rules")
    }

    @Test
    fun `the witness is a real line of play in the game engine`() {
        val board = boardWithFullRows(7)
        val pieces = tray("h4", "h4", "h4")
        val witness = assertNotNull(TraySolver.solve(board, pieces))

        val game = BlockPuzzleGame(random = Random(4))
        game.newGame()
        game.restore(
            GameState(board, pieces, 0, 0, 0, 0, 0, 0, isOver = false),
            nextUid = 200,
        )

        for (step in witness) {
            assertNotNull(
                game.place(step.pieceIndex, step.row, step.col),
                "engine rejected witness step $step",
            )
        }
        assertTrue(game.state.linesCleared > 0, "the line of play depends on a clear")
    }

    @Test
    fun `a full board admits nothing`() {
        val full = requireNotNull(Board.decode("1".repeat(BOARD_SIZE * BOARD_SIZE)))
        assertNull(TraySolver.solve(full, tray("dot", "dot", "dot")))
        assertFalse(TraySolver.canPlaceAll(full, tray("dot")))
    }

    @Test
    fun `an empty tray is trivially solvable`() {
        assertEquals(emptyList<TraySolver.Placement>(), TraySolver.solve(Board.empty(), emptyList()))
        assertTrue(TraySolver.canPlaceAll(Board.empty(), emptyList()))
    }

    @Test
    fun `a tray needing more cells than the board can ever hold is rejected`() {
        // Six full rows leaves 16 cells, and no single drop can complete a line here,
        // so three 3x3 blocks (27 cells) have nowhere to go.
        val board = boardWithFullRows(6)
        assertNull(TraySolver.solve(board, tray("sq3", "sq3", "sq3")))
    }

    @Test
    fun `the bitboard search agrees with the reference search`() {
        val ids = Shapes.ALL.map { it.id }
        var solvable = 0
        var unsolvable = 0

        for (seed in 0 until 400) {
            val rng = Random(seed)
            val board = randomBoard(rng, fill = 0.35 + rng.nextDouble() * 0.55)
            val pieces = tray(
                ids[rng.nextInt(ids.size)],
                ids[rng.nextInt(ids.size)],
                ids[rng.nextInt(ids.size)],
            )

            val fast = TraySolver.solve(board, pieces, nodeBudget = 400_000)
            val reference = TraySolver.referenceSolve(board, pieces, nodeBudget = 400_000)
            assertEquals(
                reference != null,
                fast != null,
                "seed $seed disagreed on ${pieces.map { it.shape.id }}\n$board",
            )

            if (fast != null) {
                solvable++
                assertTrue(replay(board, pieces, fast), "seed $seed: bitboard witness is not playable")
                assertTrue(
                    replay(board, pieces, requireNotNull(reference)),
                    "seed $seed: reference witness is not playable",
                )
            } else {
                unsolvable++
            }
        }

        // A cross-check that only ever saw one answer would prove nothing.
        assertTrue(solvable > 40, "expected a healthy sample of solvable trays, got $solvable")
        assertTrue(unsolvable > 40, "expected a healthy sample of dead trays, got $unsolvable")
    }

    @Test
    fun `a piece with nowhere to go is still dealt when another piece opens a space`() {
        // Seven full rows and one empty one. A 3x3 block has no home at all right now, but a
        // bar dropped into the empty row completes four columns, and the wipe makes room.
        val board = boardWithFullRows(7)
        val pieces = tray("sq3", "h4", "h4")

        assertFalse(board.hasPlacement(pieces[0]), "the 3x3 must start with nowhere to go")

        val witness = assertNotNull(TraySolver.solve(board, pieces))
        assertEquals(3, witness.size)
        assertTrue(witness.first().pieceIndex != 0, "the 3x3 cannot be played first")
        assertTrue(replay(board, pieces, witness))
    }
}
