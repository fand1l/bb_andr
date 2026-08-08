package com.blockpuzzle.game.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Thin wrapper over the vibrator with per-event intensities.
 *
 * Missing hardware, a revoked permission or an old API all degrade to doing nothing.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    var enabled: Boolean = true

    /** A piece landing on the board. */
    fun tick() = buzz(12L, 60)

    /** One or more lines going away; stronger the more lines went. */
    fun clear(lines: Int) = buzz(20L + 10L * lines.coerceIn(1, 4), 140 + 30 * lines.coerceIn(1, 4))

    /** A drop that was refused. */
    fun reject() = buzz(28L, 90)

    fun gameOver() = buzz(120L, 200)

    private fun buzz(millis: Long, amplitude: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(millis, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis)
            }
        }
    }
}
