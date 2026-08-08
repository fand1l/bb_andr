package com.blockpuzzle.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blockpuzzle.engine.BOARD_SIZE
import com.blockpuzzle.engine.Board
import com.blockpuzzle.engine.CellOffset
import com.blockpuzzle.game.ui.theme.GameColors

/** A cell that has just been wiped, remembered with the colour it had so it can fade out. */
data class ClearedCell(val row: Int, val col: Int, val colorId: Int)

/** Transient visuals layered over the board after a drop. */
data class BoardFx(
    val placed: Set<CellOffset> = emptySet(),
    val cleared: List<ClearedCell> = emptyList(),
)

/** Where the dragged piece would land, plus the lines that drop would complete. */
data class Ghost(
    val cells: List<CellOffset>,
    val colorId: Int,
    val rows: List<Int>,
    val cols: List<Int>,
)

/**
 * The 8x8 playfield.
 *
 * @param onGeometry reports the grid's top-left corner in root coordinates and the size of
 *   one cell, which is everything the drag layer needs to turn a finger position into a cell.
 */
@Composable
fun BoardView(
    board: Board,
    ghost: Ghost?,
    fx: BoardFx,
    fxProgress: Float,
    boardDescription: String,
    onGeometry: (origin: Offset, cellSize: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(GameColors.boardPlate)
            .border(1.dp, GameColors.boardPlateEdge, RoundedCornerShape(22.dp))
            .padding(7.dp)
            .semantics { contentDescription = boardDescription },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val cell = coords.size.width / BOARD_SIZE.toFloat()
                    if (cell > 0f) onGeometry(coords.positionInRoot(), cell)
                },
        ) {
            val cell = size.width / BOARD_SIZE

            // Lines the pending drop would complete, lit up before the finger lifts.
            if (ghost != null) {
                for (row in ghost.rows) {
                    drawRect(
                        color = GameColors.lineHighlight,
                        topLeft = Offset(0f, row * cell),
                        size = Size(size.width, cell),
                        alpha = 0.14f,
                    )
                }
                for (col in ghost.cols) {
                    drawRect(
                        color = GameColors.lineHighlight,
                        topLeft = Offset(col * cell, 0f),
                        size = Size(cell, size.height),
                        alpha = 0.14f,
                    )
                }
            }

            // Settled board. Cells from the drop that just happened pop in.
            for (row in 0 until BOARD_SIZE) {
                for (col in 0 until BOARD_SIZE) {
                    val topLeft = Offset(col * cell, row * cell)
                    val value = board[row, col]
                    if (value == Board.EMPTY) {
                        drawEmptyCell(GameColors.emptyCell, topLeft, cell)
                    } else {
                        val popping = CellOffset(row, col) in fx.placed
                        val scale = if (popping) 1f + 0.22f * (1f - fxProgress) else 1f
                        drawBlock(GameColors.block(value), topLeft, cell, scale = scale)
                    }
                }
            }

            // Cells wiped by this drop, expanding and fading over the now-empty slots.
            if (fx.cleared.isNotEmpty() && fxProgress < 1f) {
                for (c in fx.cleared) {
                    drawBlock(
                        color = GameColors.block(c.colorId),
                        cellTopLeft = Offset(c.col * cell, c.row * cell),
                        cellSize = cell,
                        scale = 1f + 0.55f * fxProgress,
                        alpha = 1f - fxProgress,
                    )
                }
            }

            // Drop preview.
            if (ghost != null) {
                val color = GameColors.block(ghost.colorId)
                for (g in ghost.cells) {
                    drawGhostCell(color, Offset(g.col * cell, g.row * cell), cell)
                }
            }
        }
    }
}
