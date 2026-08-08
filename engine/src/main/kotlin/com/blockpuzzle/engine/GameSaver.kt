package com.blockpuzzle.engine

/**
 * Compact, dependency-free save format so the app can persist a run in a single
 * SharedPreferences string.
 *
 * Layout (pipe separated):
 * `1|<board digits>|<score>|<best>|<combo>|<bestCombo>|<lines>|<pieces>|<over 0/1>|<nextUid>|<slot>,<slot>,<slot>`
 * where a slot is `-` for spent, or `uid:shapeId:colorId`.
 */
object GameSaver {

    private const val VERSION = 1
    private const val EMPTY_SLOT = "-"

    fun encode(state: GameState, nextUid: Int): String {
        val slots = state.tray.joinToString(",") { piece ->
            if (piece == null) EMPTY_SLOT else "${piece.uid}:${piece.shape.id}:${piece.colorId}"
        }
        return listOf(
            VERSION,
            state.board.encode(),
            state.score,
            state.best,
            state.combo,
            state.bestCombo,
            state.linesCleared,
            state.piecesPlaced,
            if (state.isOver) 1 else 0,
            nextUid,
            slots,
        ).joinToString("|")
    }

    /** Returns null for anything it does not fully understand, so a bad save is simply ignored. */
    fun decode(text: String?): SavedGame? {
        if (text.isNullOrBlank()) return null
        val parts = text.split("|")
        if (parts.size != 11) return null
        if (parts[0].toIntOrNull() != VERSION) return null

        val board = Board.decode(parts[1]) ?: return null
        val score = parts[2].toIntOrNull() ?: return null
        val best = parts[3].toIntOrNull() ?: return null
        val combo = parts[4].toIntOrNull() ?: return null
        val bestCombo = parts[5].toIntOrNull() ?: return null
        val lines = parts[6].toIntOrNull() ?: return null
        val pieces = parts[7].toIntOrNull() ?: return null
        val over = parts[8] == "1"
        val nextUid = parts[9].toIntOrNull() ?: return null

        val slots = parts[10].split(",")
        if (slots.size != PieceGenerator.TRAY_SIZE) return null
        val tray = ArrayList<Piece?>(slots.size)
        for (slot in slots) {
            if (slot == EMPTY_SLOT) {
                tray += null
                continue
            }
            val f = slot.split(":")
            if (f.size != 3) return null
            val uid = f[0].toIntOrNull() ?: return null
            val shape = Shapes.byId(f[1]) ?: return null
            val color = f[2].toIntOrNull() ?: return null
            if (color !in 1..PALETTE_SIZE) return null
            tray += Piece(uid, shape, color)
        }

        val state = GameState(
            board = board,
            tray = tray,
            score = score,
            best = best,
            combo = combo,
            bestCombo = bestCombo,
            linesCleared = lines,
            piecesPlaced = pieces,
            isOver = over,
        )
        return SavedGame(state, nextUid)
    }
}

data class SavedGame(val state: GameState, val nextUid: Int)
