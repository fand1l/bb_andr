package com.blockpuzzle.engine

import kotlin.random.Random

/**
 * The rules engine.
 *
 * Holds mutable state internally and publishes immutable [GameState] snapshots, so a
 * ViewModel can hand [state] straight to Compose without defensive copying.
 *
 * The full turn is: drop a piece into a free anchor, score its cells, wipe every row and
 * column that filled up, score the wipe against the combo counter, deal a new tray once
 * all three slots are spent, then check whether anything still fits.
 */
class BlockPuzzleGame(
    private val random: Random = Random.Default,
    private val rules: ScoreRules = ScoreRules(),
    private val boardSize: Int = BOARD_SIZE,
    fairDeals: Boolean = true,
) {

    private var generator = PieceGenerator(random = random, fair = fairDeals)

    private var board: Board = Board.empty(boardSize)
    private var tray: MutableList<Piece?> = MutableList(PieceGenerator.TRAY_SIZE) { null }
    private var score: Int = 0
    private var best: Int = 0
    private var combo: Int = 0
    private var bestCombo: Int = 0
    private var linesCleared: Int = 0
    private var piecesPlaced: Int = 0
    private var over: Boolean = false

    /** Current snapshot. Cheap to read; a fresh object every call. */
    val state: GameState
        get() = GameState(
            board = board,
            tray = tray.toList(),
            score = score,
            best = best,
            combo = combo,
            bestCombo = bestCombo,
            linesCleared = linesCleared,
            piecesPlaced = piecesPlaced,
            isOver = over,
        )

    /** Resets everything except the personal best, which survives across games. */
    fun newGame(keepBest: Int = best): GameState {
        board = Board.empty(boardSize)
        score = 0
        combo = 0
        bestCombo = 0
        linesCleared = 0
        piecesPlaced = 0
        over = false
        best = keepBest
        generator = PieceGenerator(random = random, fair = true, uidSeed = generator.lastUid + 1)
        tray = generator.nextTray(board).toMutableList<Piece?>()
        over = state.isDead()
        return state
    }

    /** Re-seeds the engine from a restored snapshot. */
    fun restore(snapshot: GameState, nextUid: Int) {
        board = snapshot.board
        tray = snapshot.tray.toMutableList()
        score = snapshot.score
        best = snapshot.best
        combo = snapshot.combo
        bestCombo = snapshot.bestCombo
        linesCleared = snapshot.linesCleared
        piecesPlaced = snapshot.piecesPlaced
        generator = PieceGenerator(random = random, fair = true, uidSeed = nextUid)
        over = snapshot.isOver || state.isDead()
    }

    /** Raises the stored personal best without touching the run in progress. */
    fun seedBest(value: Int) {
        if (value > best) best = value
    }

    /**
     * Forgets the personal best.
     *
     * The run in progress keeps its score, and that score becomes the new floor — a best
     * below the score on screen would be nonsense.
     */
    fun clearBest() {
        best = score
    }

    /** True when the piece in [trayIndex] can legally be dropped at ([row], [col]). */
    fun canPlace(trayIndex: Int, row: Int, col: Int): Boolean {
        if (over) return false
        val piece = tray.getOrNull(trayIndex) ?: return false
        return board.canPlace(piece, row, col)
    }

    /**
     * Plays one turn.
     *
     * @return the resolved [MoveResult], or null when the drop was illegal. Nothing is
     *   mutated on a rejected drop, so the caller can simply snap the piece back.
     */
    fun place(trayIndex: Int, row: Int, col: Int): MoveResult? {
        if (over) return null
        val piece = tray.getOrNull(trayIndex) ?: return null
        if (!board.canPlace(piece, row, col)) return null

        val placedCells = piece.cells.map { CellOffset(row + it.row, col + it.col) }
        val stamped = board.place(piece, row, col)
        tray[trayIndex] = null
        piecesPlaced++

        val rows = stamped.fullRows()
        val cols = stamped.fullCols()
        val clear = stamped.clearLines(rows, cols)
        board = clear.board

        val placementPoints = rules.placementScore(piece.size)
        val lines = rows.size + cols.size
        if (lines > 0) {
            combo++
            linesCleared += lines
            if (combo > bestCombo) bestCombo = combo
        } else {
            combo = 0
        }
        val clearPoints = rules.clearScore(lines, combo)
        val allClearPoints = if (lines > 0 && board.isClear) rules.allClearBonus else 0

        score += placementPoints + clearPoints + allClearPoints
        val newBest = score > best
        if (newBest) best = score

        val refilled = tray.all { it == null }
        if (refilled) {
            val dealt = generator.nextTray(board)
            for (i in tray.indices) tray[i] = dealt.getOrNull(i)
        }

        over = state.isDead()

        return MoveResult(
            placedCells = placedCells,
            placedColorId = piece.colorId,
            boardAfterPlace = stamped,
            clearedRows = rows,
            clearedCols = cols,
            clearedCells = clear.clearedCells,
            placementScore = placementPoints,
            clearScore = clearPoints,
            allClearBonus = allClearPoints,
            combo = if (lines > 0) combo else 0,
            totalScore = score,
            newBest = newBest,
            trayRefilled = refilled,
            gameOver = over,
        )
    }

    /**
     * Best-effort hint: the first anchor where the piece in [trayIndex] fits, preferring
     * anchors that complete at least one line.
     */
    fun hint(trayIndex: Int): CellOffset? {
        val piece = tray.getOrNull(trayIndex) ?: return null
        val spots = board.placements(piece.cells)
        if (spots.isEmpty()) return null
        return spots.firstOrNull { spot ->
            val after = board.place(piece, spot.row, spot.col)
            after.fullRows().isNotEmpty() || after.fullCols().isNotEmpty()
        } ?: spots.first()
    }

    /** uid the next generated piece will take; persisted alongside the board. */
    fun nextUid(): Int = generator.lastUid + 1
}
