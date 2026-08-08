package com.blockpuzzle.engine

/**
 * Coordinate of a single filled cell.
 *
 * Inside a [Shape] it is relative to the shape's top-left bounding box corner.
 * Inside a [MoveResult] it is an absolute board coordinate.
 */
data class CellOffset(val row: Int, val col: Int)

/**
 * A piece silhouette from the catalogue.
 *
 * Shapes carry no colour: the colour is picked when a concrete [Piece] is generated,
 * exactly like in the games this one is modelled on.
 *
 * @param weight relative frequency used by [PieceGenerator]. Bigger, harder to place
 *   silhouettes get a lower weight so the board does not choke on 3x3 blocks.
 */
data class Shape(
    val id: String,
    val cells: List<CellOffset>,
    val weight: Int,
) {
    val height: Int = cells.maxOf { it.row } + 1
    val width: Int = cells.maxOf { it.col } + 1
    val size: Int = cells.size
}

/**
 * Builds a [Shape] from ASCII art. `#` marks a filled cell, anything else is empty.
 * The result is normalised so its bounding box starts at (0, 0).
 */
private fun shape(id: String, weight: Int, vararg rows: String): Shape {
    val cells = ArrayList<CellOffset>()
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch -> if (ch == '#') cells += CellOffset(r, c) }
    }
    require(cells.isNotEmpty()) { "Shape '$id' has no filled cells" }
    val minRow = cells.minOf { it.row }
    val minCol = cells.minOf { it.col }
    val normalised =
        if (minRow == 0 && minCol == 0) cells
        else cells.map { CellOffset(it.row - minRow, it.col - minCol) }
    return Shape(id, normalised, weight)
}

/** The full silhouette catalogue: 1..5 cell lines, blocks, corners, tetrominoes, plus and diagonals. */
object Shapes {

    val ALL: List<Shape> = listOf(
        // --- dots and straight lines -------------------------------------------------
        shape("dot", 90, "#"),
        shape("h2", 85, "##"),
        shape("v2", 85, "#", "#"),
        shape("h3", 75, "###"),
        shape("v3", 75, "#", "#", "#"),
        shape("h4", 50, "####"),
        shape("v4", 50, "#", "#", "#", "#"),
        shape("h5", 26, "#####"),
        shape("v5", 26, "#", "#", "#", "#", "#"),

        // --- solid blocks ------------------------------------------------------------
        shape("sq2", 70, "##", "##"),
        shape("rect23", 28, "###", "###"),
        shape("rect32", 28, "##", "##", "##"),
        shape("sq3", 14, "###", "###", "###"),

        // --- small corners (3 cells) -------------------------------------------------
        shape("corner_tl", 58, "##", "#."),
        shape("corner_tr", 58, "##", ".#"),
        shape("corner_bl", 58, "#.", "##"),
        shape("corner_br", 58, ".#", "##"),

        // --- big corners (5 cells, 3x3 bounding box) ---------------------------------
        shape("big_tl", 24, "###", "#..", "#.."),
        shape("big_tr", 24, "###", "..#", "..#"),
        shape("big_bl", 24, "#..", "#..", "###"),
        shape("big_br", 24, "..#", "..#", "###"),

        // --- J tetromino (4 rotations) -----------------------------------------------
        shape("j0", 32, "#..", "###"),
        shape("j1", 32, "##", "#.", "#."),
        shape("j2", 32, "###", "..#"),
        shape("j3", 32, ".#", ".#", "##"),

        // --- L tetromino (4 rotations) -----------------------------------------------
        shape("l0", 32, "..#", "###"),
        shape("l1", 32, "#.", "#.", "##"),
        shape("l2", 32, "###", "#.."),
        shape("l3", 32, "##", ".#", ".#"),

        // --- T tetromino (4 rotations) -----------------------------------------------
        shape("t0", 28, "###", ".#."),
        shape("t1", 28, ".#", "##", ".#"),
        shape("t2", 28, ".#.", "###"),
        shape("t3", 28, "#.", "##", "#."),

        // --- S / Z tetrominoes -------------------------------------------------------
        shape("s0", 20, ".##", "##."),
        shape("s1", 20, "#.", "##", ".#"),
        shape("z0", 20, "##.", ".##"),
        shape("z1", 20, ".#", "##", "#."),

        // --- plus --------------------------------------------------------------------
        shape("plus", 12, ".#.", "###", ".#."),

        // --- diagonals ---------------------------------------------------------------
        shape("diag2a", 14, "#.", ".#"),
        shape("diag2b", 14, ".#", "#."),
        shape("diag3a", 7, "#..", ".#.", "..#"),
        shape("diag3b", 7, "..#", ".#.", "#.."),
    )

    private val byId: Map<String, Shape> = ALL.associateBy { it.id }

    fun byId(id: String): Shape? = byId[id]
}
