package com.blockpuzzle.engine

/**
 * A concrete piece sitting in the tray: a [Shape] plus the colour it was rolled with.
 *
 * @param uid stable identity for the lifetime of this piece. The UI keys its tray
 *   animations on it, so it must survive save/restore.
 * @param colorId 1-based index into the palette. 0 is reserved for "empty cell".
 */
data class Piece(
    val uid: Int,
    val shape: Shape,
    val colorId: Int,
) {
    val cells: List<CellOffset> get() = shape.cells
    val width: Int get() = shape.width
    val height: Int get() = shape.height
    val size: Int get() = shape.size
}

/** Number of distinct block colours in the palette (colour ids are 1..[PALETTE_SIZE]). */
const val PALETTE_SIZE: Int = 8
