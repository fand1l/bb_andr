package com.blockpuzzle.game.data

import android.content.Context
import android.content.SharedPreferences

/**
 * All persistence the game needs: the personal best, the run in progress and two toggles.
 *
 * Everything lives in one SharedPreferences file so the backup rules can name it explicitly.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(value) = prefs.edit().putInt(KEY_BEST, value).apply()

    /** Serialised [com.blockpuzzle.engine.GameSaver] payload, or null when there is no run to resume. */
    var savedGame: String?
        get() = prefs.getString(KEY_SAVE, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SAVE) else putString(KEY_SAVE, value)
            }.apply()
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    companion object {
        /** Referenced by name from res/xml/backup_rules.xml. */
        const val FILE = "block_puzzle_prefs"

        private const val KEY_BEST = "best_score"
        private const val KEY_SAVE = "saved_game"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
    }
}
