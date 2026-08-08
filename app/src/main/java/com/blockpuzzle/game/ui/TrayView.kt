package com.blockpuzzle.game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blockpuzzle.engine.Piece
import com.blockpuzzle.engine.PieceGenerator

/**
 * The three-piece tray.
 *
 * A slot only reports gestures; the screen above owns the drag session so the floating
 * piece can be drawn over the board rather than clipped inside the tray.
 *
 * @param onDragStart receives the slot index, the touch point in root coordinates, and where
 *   inside the piece's bounding box the finger grabbed it, as a 0..1 fraction on each axis.
 *   Preserving that fraction is what makes the piece feel held rather than snapped to a corner.
 */
@Composable
fun TrayView(
    tray: List<Piece?>,
    draggingIndex: Int?,
    enabled: Boolean,
    trayDescription: String,
    onDragStart: (index: Int, pointerInRoot: Offset, grabFraction: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = trayDescription },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until PieceGenerator.TRAY_SIZE) {
            TraySlot(
                index = index,
                piece = tray.getOrNull(index),
                isDragging = draggingIndex == index,
                enabled = enabled,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun TraySlot(
    index: Int,
    piece: Piece?,
    isDragging: Boolean,
    enabled: Boolean,
    onDragStart: (Int, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var slotOrigin by remember { mutableStateOf(Offset.Zero) }
    var slotSize by remember { mutableStateOf(Size.Zero) }

    // Fresh pieces grow into place; a piece being dragged leaves an empty slot behind.
    val appear by animateFloatAsState(
        targetValue = if (piece == null || isDragging) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "tray-slot-$index",
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                slotOrigin = coords.positionInRoot()
                slotSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            .pointerInput(piece?.uid, enabled) {
                val held = piece
                if (held == null || !enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { local ->
                        val cell = trayCellSize(slotSize)
                        val pivot = piecePivotInSlot(held, slotSize, cell)
                        val pieceWidth = held.width * cell
                        val pieceHeight = held.height * cell
                        val fractionX =
                            if (pieceWidth > 0f) ((local.x - pivot.x) / pieceWidth).coerceIn(0f, 1f) else 0.5f
                        val fractionY =
                            if (pieceHeight > 0f) ((local.y - pivot.y) / pieceHeight).coerceIn(0f, 1f) else 0.5f
                        onDragStart(index, slotOrigin + local, Offset(fractionX, fractionY))
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            },
    ) {
        if (piece != null && appear > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = trayCellSize(size)
                val pivot = piecePivotInSlot(piece, size, cell)
                drawPiece(
                    piece = piece,
                    origin = pivot,
                    cellSize = cell,
                    alpha = appear.coerceIn(0f, 1f),
                    scale = appear.coerceIn(0f, 1f),
                )
            }
        }
    }
}
