package com.blockpuzzle.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockpuzzle.engine.CellOffset
import com.blockpuzzle.engine.Piece
import com.blockpuzzle.game.GameViewModel
import com.blockpuzzle.game.R
import com.blockpuzzle.game.ui.theme.GameColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A piece currently held by the finger. */
private data class DragSession(
    val trayIndex: Int,
    val piece: Piece,
    /** Touch point in root coordinates. */
    val pointer: Offset,
    /** Where inside the piece's bounding box it was grabbed, 0..1 per axis. */
    val grab: Offset,
)

/** The cell a drag currently points at, plus the lines it would complete. */
private data class DragTarget(val row: Int, val col: Int, val ghost: Ghost?)

/** Floating "+N" shown after a scoring drop. */
private data class ScorePopup(val gained: Int, val combo: Int, val allClear: Boolean)

/** How far above the finger the held piece floats, in board cells. */
private const val DRAG_LIFT_CELLS = 1.15f

@Composable
fun GameScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val state = viewModel.state

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardCell by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableStateOf<DragSession?>(null) }

    var showSettings by remember { mutableStateOf(false) }
    var showGameOver by remember { mutableStateOf(false) }

    var fx by remember { mutableStateOf(BoardFx()) }
    val fxProgress = remember { Animatable(1f) }
    var popup by remember { mutableStateOf<ScorePopup?>(null) }
    val popupProgress = remember { Animatable(1f) }

    // Board effects: the dropped piece pops in while the wiped cells expand away.
    LaunchedEffect(viewModel.moveSerial) {
        val move = viewModel.lastMove
        if (move == null) {
            fx = BoardFx()
            return@LaunchedEffect
        }
        fx = BoardFx(
            placed = move.placedCells.toSet(),
            cleared = move.clearedCells.map {
                ClearedCell(it.row, it.col, move.boardAfterPlace[it.row, it.col])
            },
        )
        fxProgress.snapTo(0f)
        fxProgress.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
        fx = BoardFx()
    }

    // Score popup runs on its own clock so it outlives the board animation.
    LaunchedEffect(viewModel.moveSerial) {
        val move = viewModel.lastMove
        if (move == null || move.gained <= 0) {
            popup = null
            return@LaunchedEffect
        }
        popup = ScorePopup(move.gained, move.combo, move.isAllClear)
        popupProgress.snapTo(0f)
        popupProgress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        popup = null
    }

    // Let the final board settle before the dialog covers it.
    LaunchedEffect(state.isOver, viewModel.moveSerial) {
        if (state.isOver) {
            delay(750)
            showGameOver = viewModel.state.isOver
        } else {
            showGameOver = false
        }
    }

    val dragTarget: DragTarget? = remember(drag, boardOrigin, boardCell, state) {
        val session = drag ?: return@remember null
        if (boardCell <= 0f) return@remember null
        val pieceWidth = session.piece.width * boardCell
        val pieceHeight = session.piece.height * boardCell
        val topLeftX = session.pointer.x - session.grab.x * pieceWidth
        val topLeftY = session.pointer.y - session.grab.y * pieceHeight - boardCell * DRAG_LIFT_CELLS
        val col = ((topLeftX - boardOrigin.x) / boardCell).roundToInt()
        val row = ((topLeftY - boardOrigin.y) / boardCell).roundToInt()

        val preview = viewModel.preview(session.trayIndex, row, col)
        val ghost = preview?.let {
            Ghost(
                cells = session.piece.cells.map { c -> CellOffset(row + c.row, col + c.col) },
                colorId = session.piece.colorId,
                rows = it.rows,
                cols = it.cols,
            )
        }
        DragTarget(row, col, ghost)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GameColors.backgroundBrush)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                best = state.best,
                onSettings = { showSettings = true },
                onRestart = { viewModel.newGame() },
            )

            ScoreHeader(score = state.score)

            PopupBanner(popup = popup, progress = popupProgress.value)

            BoardView(
                board = state.board,
                ghost = dragTarget?.ghost,
                fx = fx,
                fxProgress = fxProgress.value,
                boardDescription = stringResource(R.string.cd_board),
                onGeometry = { origin, cell ->
                    boardOrigin = origin
                    boardCell = cell
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            TrayView(
                tray = state.tray,
                draggingIndex = drag?.trayIndex,
                enabled = !state.isOver,
                trayDescription = stringResource(R.string.cd_tray),
                onDragStart = { index, pointer, grab ->
                    val piece = viewModel.pieceAt(index)
                    if (piece != null && !state.isOver) {
                        drag = DragSession(index, piece, pointer, grab)
                    }
                },
                onDrag = { delta ->
                    drag = drag?.let { it.copy(pointer = it.pointer + delta) }
                },
                onDragEnd = {
                    val session = drag
                    val target = dragTarget
                    if (session != null && target?.ghost != null) {
                        viewModel.place(session.trayIndex, target.row, target.col)
                    }
                    drag = null
                },
                onDragCancel = { drag = null },
            )

            Spacer(Modifier.height(18.dp))
        }

        // Held piece, drawn last so it floats above everything including the tray.
        val session = drag
        if (session != null && boardCell > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val pieceWidth = session.piece.width * boardCell
                val pieceHeight = session.piece.height * boardCell
                val origin = Offset(
                    x = session.pointer.x - session.grab.x * pieceWidth - rootOrigin.x,
                    y = session.pointer.y - session.grab.y * pieceHeight -
                        boardCell * DRAG_LIFT_CELLS - rootOrigin.y,
                )
                drawPiece(session.piece, origin, boardCell, alpha = 0.94f)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            soundEnabled = viewModel.soundEnabled,
            hapticsEnabled = viewModel.hapticsEnabled,
            onSoundChange = viewModel::setSoundEnabled,
            onHapticsChange = viewModel::setHapticsEnabled,
            onRestart = {
                viewModel.newGame()
                showSettings = false
            },
            onResetBest = {
                viewModel.resetBest()
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }

    if (showGameOver) {
        GameOverDialog(
            score = state.score,
            best = state.best,
            isNewBest = viewModel.beatenRecordThisRun,
            linesCleared = state.linesCleared,
            piecesPlaced = state.piecesPlaced,
            bestCombo = state.bestCombo,
            onPlayAgain = {
                showGameOver = false
                viewModel.newGame()
            },
            onDismiss = { showGameOver = false },
        )
    }
}

@Composable
private fun TopBar(
    best: Int,
    onSettings: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = GameColors.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GameColors.panel)
                .border(1.dp, GameColors.panelEdge, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.best).uppercase(),
                color = GameColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = best.toString(),
                color = GameColors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        IconButton(onClick = onRestart) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.cd_restart),
                tint = GameColors.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun ScoreHeader(score: Int) {
    // Rolling the number up reads as "points arriving" instead of a jump cut.
    val shown by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "score",
    )
    Text(
        text = shown.toString(),
        color = GameColors.textPrimary,
        fontSize = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun PopupBanner(popup: ScorePopup?, progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (popup == null) return@Box

        // Fade in fast, hold, then fade out over the tail of the animation.
        val alpha = when {
            progress < 0.12f -> progress / 0.12f
            progress > 0.65f -> ((1f - progress) / 0.35f).coerceIn(0f, 1f)
            else -> 1f
        }
        val riseDp = -18f * progress

        Column(
            modifier = Modifier
                .offset { IntOffset(0, riseDp.dp.roundToPx()) }
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "+${popup.gained}",
                color = if (popup.combo >= 2) GameColors.accent else GameColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            if (popup.allClear) {
                Text(
                    text = stringResource(R.string.all_clear),
                    color = GameColors.BLOCKS[8],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else if (popup.combo >= 2) {
                Text(
                    text = stringResource(R.string.combo_value, popup.combo),
                    color = GameColors.BLOCKS[6],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
