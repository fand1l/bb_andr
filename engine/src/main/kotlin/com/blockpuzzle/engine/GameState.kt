package com.blockpuzzle.engine

/**
 * Immutable snapshot of everything the UI needs to draw a frame.
 *
 * @param tray always [PieceGenerator.TRAY_SIZE] long; a used slot is null until the whole
 *   tray empties and a new one is dealt.
 * @param combo length of the current clearing streak; 0 when the last drop wiped nothing.
 * @param dealsSinceGift deals since the last tailored tray; persisted so the cooldown cannot
 *   be reset by closing the app.
 */
data class GameState(
    val board: Board,
    val tray: List<Piece?>,
    val score: Int,
    val best: Int,
    val combo: Int,
    val bestCombo: Int,
    val linesCleared: Int,
    val piecesPlaced: Int,
    val isOver: Boolean,
    val dealsSinceGift: Int = 0,
) {
    val trayPieces: List<Piece> get() = tray.filterNotNull()

    /** True when no remaining tray piece fits anywhere — the losing condition. */
    fun isDead(): Boolean = trayPieces.none { board.hasPlacement(it) }
}

/** Everything that happened during one successful drop, in the order the UI should show it. */
data class MoveResult(
    /** Absolute cells the piece now occupies. */
    val placedCells: List<CellOffset>,
    val placedColorId: Int,
    /** Board with the piece stamped but before any line was wiped. */
    val boardAfterPlace: Board,
    val clearedRows: List<Int>,
    val clearedCols: List<Int>,
    /** Absolute cells wiped by the clear, de-duplicated across the row/column overlap. */
    val clearedCells: List<CellOffset>,
    val placementScore: Int,
    val clearScore: Int,
    val allClearBonus: Int,
    /** Combo level this drop scored at; 0 when nothing was wiped. */
    val combo: Int,
    val totalScore: Int,
    val newBest: Boolean,
    /** True when this drop emptied the tray and a fresh one was dealt. */
    val trayRefilled: Boolean,
    val gameOver: Boolean,
) {
    val linesCleared: Int get() = clearedRows.size + clearedCols.size
    val gained: Int get() = placementScore + clearScore + allClearBonus
    val isAllClear: Boolean get() = allClearBonus > 0
}
