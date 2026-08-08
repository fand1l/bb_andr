package com.blockpuzzle.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blockpuzzle.engine.BlockPuzzleGame
import com.blockpuzzle.engine.GameSaver
import com.blockpuzzle.engine.MoveResult
import com.blockpuzzle.engine.Piece
import com.blockpuzzle.game.audio.SoundFx
import com.blockpuzzle.game.data.Prefs
import com.blockpuzzle.game.util.Haptics

/** Rows and columns a pending drop would wipe, used to pre-highlight them under the finger. */
data class DropPreview(val rows: List<Int>, val cols: List<Int>)

/**
 * Owns the engine, the persisted state and the feedback channels.
 *
 * The UI reads [state] for what to draw and watches [moveSerial] to know when a fresh
 * [lastMove] is worth animating.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val game = BlockPuzzleGame()
    private val sound = SoundFx(application)
    private val haptics = Haptics(application)

    var state by mutableStateOf(game.state)
        private set

    /** The most recent successful drop; null until the first move of a run. */
    var lastMove by mutableStateOf<MoveResult?>(null)
        private set

    /** Bumped on every successful drop so animations restart even for identical results. */
    var moveSerial by mutableIntStateOf(0)
        private set

    var soundEnabled by mutableStateOf(prefs.soundEnabled)
        private set

    var hapticsEnabled by mutableStateOf(prefs.hapticsEnabled)
        private set

    /** True once any drop in the current run has beaten the stored record. */
    var beatenRecordThisRun by mutableStateOf(false)
        private set

    init {
        sound.enabled = soundEnabled
        haptics.enabled = hapticsEnabled
        resumeOrStart()
    }

    private fun resumeOrStart() {
        val saved = GameSaver.decode(prefs.savedGame)
        if (saved != null && !saved.state.isOver) {
            game.restore(saved.state, saved.nextUid)
            game.seedBest(prefs.bestScore)
        } else {
            game.newGame(prefs.bestScore)
        }
        state = game.state
        persist()
    }

    /** Starts a fresh run, keeping the personal best. */
    fun newGame() {
        game.newGame(prefs.bestScore)
        state = game.state
        lastMove = null
        beatenRecordThisRun = false
        moveSerial++
        persist()
    }

    fun pieceAt(trayIndex: Int): Piece? = state.tray.getOrNull(trayIndex)

    /** Which lines a drop at ([row], [col]) would complete, or null when the drop is illegal. */
    fun preview(trayIndex: Int, row: Int, col: Int): DropPreview? {
        val piece = state.tray.getOrNull(trayIndex) ?: return null
        if (state.isOver || !state.board.canPlace(piece, row, col)) return null
        val after = state.board.place(piece, row, col)
        return DropPreview(after.fullRows(), after.fullCols())
    }

    /**
     * Plays a turn and fires the matching feedback.
     *
     * @return true when the drop was legal. A rejected drop only buzzes, nothing changes.
     */
    fun place(trayIndex: Int, row: Int, col: Int): Boolean {
        val move = game.place(trayIndex, row, col)
        if (move == null) {
            haptics.reject()
            return false
        }

        state = game.state
        lastMove = move
        moveSerial++
        if (move.newBest) beatenRecordThisRun = true

        if (move.linesCleared > 0) {
            sound.clear(move.linesCleared, move.combo)
            haptics.clear(move.linesCleared)
        } else {
            sound.drop()
            haptics.tick()
        }
        if (move.gameOver) {
            sound.gameOver()
            haptics.gameOver()
        }

        persist()
        return true
    }

    fun setSoundEnabled(value: Boolean) {
        soundEnabled = value
        sound.enabled = value
        prefs.soundEnabled = value
    }

    fun setHapticsEnabled(value: Boolean) {
        hapticsEnabled = value
        haptics.enabled = value
        prefs.hapticsEnabled = value
    }

    /**
     * Clears the stored personal best without interrupting the run.
     *
     * The score already on screen becomes the new best, since a record below the current
     * score would be meaningless.
     */
    fun resetBest() {
        prefs.bestScore = 0
        game.clearBest()
        state = game.state
        beatenRecordThisRun = false
        persist()
    }

    /** Called when the app is backgrounded, so a kill mid-session still resumes correctly. */
    fun persist() {
        prefs.savedGame = GameSaver.encode(state, game.nextUid())
        if (state.best > prefs.bestScore) prefs.bestScore = state.best
    }

    override fun onCleared() {
        sound.release()
        super.onCleared()
    }
}
