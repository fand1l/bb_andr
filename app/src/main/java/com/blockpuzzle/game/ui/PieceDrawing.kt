package com.blockpuzzle.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.blockpuzzle.engine.Piece
import com.blockpuzzle.game.ui.theme.GameColors
import kotlin.math.min

/** Draws a whole piece with its bounding-box top-left corner at [origin]. */
fun DrawScope.drawPiece(
    piece: Piece,
    origin: Offset,
    cellSize: Float,
    alpha: Float = 1f,
    scale: Float = 1f,
) {
    val color = GameColors.block(piece.colorId)
    for (cell in piece.cells) {
        drawBlock(
            color = color,
            cellTopLeft = Offset(origin.x + cell.col * cellSize, origin.y + cell.row * cellSize),
            cellSize = cellSize,
            scale = scale,
            alpha = alpha,
        )
    }
}

/**
 * Cell size a tray slot of [slotSize] should use.
 *
 * Divided so the longest silhouette in the catalogue (five cells) leaves a visible margin,
 * which keeps every slot's pieces at a consistent scale instead of stretching each to fit.
 */
fun trayCellSize(slotSize: Size): Float = min(slotSize.width, slotSize.height) / 5.4f

/** Top-left corner that centres [piece] inside a slot of [slotSize] at [cellSize]. */
fun piecePivotInSlot(piece: Piece, slotSize: Size, cellSize: Float): Offset = Offset(
    x = (slotSize.width - piece.width * cellSize) / 2f,
    y = (slotSize.height - piece.height * cellSize) / 2f,
)
