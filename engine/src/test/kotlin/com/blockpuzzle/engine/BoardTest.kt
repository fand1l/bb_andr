package com.blockpuzzle.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun piece(shapeId: String, color: Int = 1, uid: Int = 1) =
    Piece(uid, requireNotNull(Shapes.byId(shapeId)) { "unknown shape $shapeId" }, color)

/** Builds a board from ASCII art; `.` is empty, any other char is a filled cell of colour 1. */
private fun boardOf(vararg rows: String): Board {
    require(rows.size == BOARD_SIZE) { "need $BOARD_SIZE rows" }
    val sb = StringBuilder()
    for (row in rows) {
        require(row.length == BOARD_SIZE) { "need $BOARD_SIZE columns, got '${row}'" }
        for (ch in row) sb.append(if (ch == '.') '0' else '1')
    }
    return requireNotNull(Board.decode(sb.toString()))
}

class ShapesTest {

    @Test
    fun `every shape is normalised to the origin`() {
        for (shape in Shapes.ALL) {
            assertEquals(0, shape.cells.minOf { it.row }, "${shape.id} top")
            assertEquals(0, shape.cells.minOf { it.col }, "${shape.id} left")
        }
    }

    @Test
    fun `shape ids are unique and every shape fits an empty board`() {
        val ids = Shapes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate shape ids")
        val empty = Board.empty()
        for (shape in Shapes.ALL) {
            assertTrue(shape.width <= BOARD_SIZE && shape.height <= BOARD_SIZE, "${shape.id} too big")
            assertTrue(empty.hasPlacement(shape.cells), "${shape.id} cannot be placed on an empty board")
            assertTrue(shape.weight > 0, "${shape.id} has no weight")
        }
    }

    @Test
    fun `catalogue covers the expected silhouette families`() {
        val ids = Shapes.ALL.map { it.id }.toSet()
        listOf("dot", "h5", "v5", "sq2", "sq3", "plus", "t0", "s0", "z0", "big_tl", "diag2a")
            .forEach { assertTrue(it in ids, "missing shape $it") }
        assertEquals(5, requireNotNull(Shapes.byId("plus")).size)
        assertEquals(9, requireNotNull(Shapes.byId("sq3")).size)
        assertEquals(1, requireNotNull(Shapes.byId("dot")).size)
    }
}

class BoardPlacementTest {

    @Test
    fun `placement is rejected off the board`() {
        val board = Board.empty()
        val p = piece("h5")
        assertTrue(board.canPlace(p, 0, 0))
        assertTrue(board.canPlace(p, 7, 3))
        assertFalse(board.canPlace(p, 0, 4), "5-wide piece cannot start at column 4")
        assertFalse(board.canPlace(p, 8, 0))
        assertFalse(board.canPlace(p, -1, 0))
    }

    @Test
    fun `placement is rejected on an occupied cell`() {
        val board = Board.empty().place(piece("dot", color = 3), 4, 4)
        assertEquals(3, board[4, 4])
        assertFalse(board.canPlace(piece("dot"), 4, 4))
        assertTrue(board.canPlace(piece("dot"), 4, 5))
        assertFalse(board.canPlace(piece("h3"), 4, 2), "would overlap (4,4)")
    }

    @Test
    fun `place stamps every cell with the piece colour and leaves the source untouched`() {
        val before = Board.empty()
        val after = before.place(piece("corner_tl", color = 6), 2, 3)
        assertEquals(0, before.filledCount, "original board must be immutable")
        assertEquals(3, after.filledCount)
        assertEquals(6, after[2, 3])
        assertEquals(6, after[2, 4])
        assertEquals(6, after[3, 3])
        assertTrue(after.isEmpty(3, 4))
    }

    @Test
    fun `hasPlacement finds the only remaining hole`() {
        val board = boardOf(
            "########",
            "########",
            "########",
            "#######.",
            "########",
            "########",
            "########",
            "########",
        )
        assertTrue(board.hasPlacement(piece("dot")))
        assertFalse(board.hasPlacement(piece("h2")))
        assertEquals(listOf(CellOffset(3, 7)), board.placements(piece("dot").cells))
    }

    @Test
    fun `placements enumerates every anchor on an empty board`() {
        val board = Board.empty()
        assertEquals(64, board.placements(piece("dot").cells).size)
        assertEquals(49, board.placements(piece("sq2").cells).size)
        assertEquals(8 * 4, board.placements(piece("h5").cells).size)
    }
}

class BoardClearTest {

    @Test
    fun `a completed row is detected and wiped`() {
        val board = boardOf(
            "########",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        assertEquals(listOf(0), board.fullRows())
        assertEquals(emptyList(), board.fullCols())
        val result = board.clearLines(board.fullRows(), board.fullCols())
        assertEquals(8, result.clearedCells.size)
        assertTrue(result.board.isClear)
    }

    @Test
    fun `a completed column is detected and wiped`() {
        val board = boardOf(
            "..#.....",
            "..#.....",
            "..#.....",
            "..#.....",
            "..#.....",
            "..#.....",
            "..#.....",
            "..#.....",
        )
        assertEquals(listOf(2), board.fullCols())
        val result = board.clearLines(emptyList(), board.fullCols())
        assertTrue(result.board.isClear)
        assertEquals(8, result.clearedCells.size)
    }

    @Test
    fun `row and column clearing together counts the shared cell once`() {
        val board = boardOf(
            "########",
            "#.......",
            "#.......",
            "#.......",
            "#.......",
            "#.......",
            "#.......",
            "#.......",
        )
        assertEquals(listOf(0), board.fullRows())
        assertEquals(listOf(0), board.fullCols())
        val result = board.clearLines(board.fullRows(), board.fullCols())
        assertEquals(15, result.clearedCells.size, "8 + 8 minus the shared corner")
        assertEquals(result.clearedCells.size, result.clearedCells.toSet().size, "no duplicates")
        assertTrue(result.board.isClear)
    }

    @Test
    fun `clearing leaves untouched rows in place`() {
        val board = boardOf(
            "########",
            "##......",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        val result = board.clearLines(board.fullRows(), board.fullCols())
        assertEquals(2, result.board.filledCount)
        assertTrue(result.board.isEmpty(0, 0))
        assertFalse(result.board.isEmpty(1, 0))
    }

    @Test
    fun `clearing nothing returns the same board`() {
        val board = Board.empty().place(piece("dot"), 1, 1)
        val result = board.clearLines(emptyList(), emptyList())
        assertEquals(board, result.board)
        assertTrue(result.clearedCells.isEmpty())
    }
}

class BoardCodecTest {

    @Test
    fun `encode decode round trips`() {
        var board = Board.empty()
        board = board.place(piece("sq3", color = 4), 1, 1)
        board = board.place(piece("h5", color = 7), 6, 2)
        val restored = requireNotNull(Board.decode(board.encode()))
        assertEquals(board, restored)
        assertEquals(4, restored[1, 1])
        assertEquals(7, restored[6, 2])
    }

    @Test
    fun `decode rejects malformed payloads`() {
        assertNull(Board.decode(""))
        assertNull(Board.decode("012"))
        assertNull(Board.decode("0".repeat(63)))
        assertNull(Board.decode("9".repeat(64)), "colour ids above the palette are invalid")
        assertNull(Board.decode("x".repeat(64)))
    }
}
