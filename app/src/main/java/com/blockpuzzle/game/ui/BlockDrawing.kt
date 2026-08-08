package com.blockpuzzle.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.blockpuzzle.game.ui.theme.shade
import com.blockpuzzle.game.ui.theme.tint

/** Fraction of a cell left as a gap between neighbouring blocks. */
private const val CELL_GAP = 0.06f

/** Corner rounding as a fraction of the drawn block. */
private const val CORNER = 0.26f

/** How much of the block is the darker bottom lip that gives it depth. */
private const val LIP = 0.13f

/**
 * Draws one glossy block filling the cell whose top-left corner is [cellTopLeft].
 *
 * The block is built from three stacked rounded rectangles — a dark base, the coloured
 * face sitting slightly above it, and a soft highlight near the top — which is what gives
 * the pieces their moulded, candy-like look without needing any bitmaps.
 *
 * @param scale 1 draws the block at full cell size; smaller values shrink it about its centre.
 * @param alpha overall opacity, used by the ghost preview and the clear animation.
 */
fun DrawScope.drawBlock(
    color: Color,
    cellTopLeft: Offset,
    cellSize: Float,
    scale: Float = 1f,
    alpha: Float = 1f,
) {
    if (alpha <= 0.01f || scale <= 0.01f) return

    val gap = cellSize * CELL_GAP
    val full = cellSize - gap * 2f
    val side = full * scale
    val centreShift = (full - side) / 2f
    val x = cellTopLeft.x + gap + centreShift
    val y = cellTopLeft.y + gap + centreShift
    val radius = CornerRadius(side * CORNER, side * CORNER)

    // Dark base: the part that peeks out along the bottom edge.
    drawRoundRect(
        color = color.shade(0.42f),
        topLeft = Offset(x, y),
        size = Size(side, side),
        cornerRadius = radius,
        alpha = alpha,
    )

    // Coloured face.
    val faceHeight = side * (1f - LIP)
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(side, faceHeight),
        cornerRadius = radius,
        alpha = alpha,
    )

    // Top highlight.
    val hlInset = side * 0.16f
    drawRoundRect(
        color = color.tint(0.55f),
        topLeft = Offset(x + hlInset, y + side * 0.11f),
        size = Size(side - hlInset * 2f, side * 0.2f),
        cornerRadius = CornerRadius(side * 0.1f, side * 0.1f),
        alpha = alpha * 0.55f,
    )
}

/** Draws the recessed slot of an unoccupied grid cell. */
fun DrawScope.drawEmptyCell(
    color: Color,
    cellTopLeft: Offset,
    cellSize: Float,
    alpha: Float = 1f,
) {
    val gap = cellSize * CELL_GAP
    val side = cellSize - gap * 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(cellTopLeft.x + gap, cellTopLeft.y + gap),
        size = Size(side, side),
        cornerRadius = CornerRadius(side * CORNER, side * CORNER),
        alpha = alpha,
    )
}

/** Outline drawn under a valid drop preview so the target cells read clearly. */
fun DrawScope.drawGhostCell(
    color: Color,
    cellTopLeft: Offset,
    cellSize: Float,
) {
    drawBlock(color, cellTopLeft, cellSize, scale = 0.94f, alpha = 0.42f)
}
