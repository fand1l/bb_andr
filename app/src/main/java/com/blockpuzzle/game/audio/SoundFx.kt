package com.blockpuzzle.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sound effects with no bundled audio assets.
 *
 * The three cues are synthesised into small WAV files in the cache directory the first
 * time the game runs, then handed to a [SoundPool]. Pitch is varied at playback time, so
 * one "pop" sample covers the whole combo ladder.
 *
 * Every entry point is defensive: if audio is unavailable for any reason the game simply
 * plays silently rather than crashing.
 */
class SoundFx(context: Context) {

    private val appContext = context.applicationContext

    private var pool: SoundPool? = null
    private var dropId = 0
    private var popId = 0
    private var overId = 0
    private var ready = false

    var enabled: Boolean = true

    init {
        runCatching { setUp() }.onFailure { Log.w(TAG, "sound disabled: ${it.message}") }
    }

    private fun setUp() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attributes)
            .build()

        val dir = File(appContext.cacheDir, "sfx").apply { mkdirs() }

        val drop = writeWav(File(dir, "drop.wav")) { t, duration ->
            // Short wooden click: a low tone with a fast decay.
            val env = exp(-24.0 * t)
            (sin(2 * PI * 320 * t) * 0.7 + sin(2 * PI * 640 * t) * 0.3) * env * (1 - t / duration)
        }
        val pop = writeWav(File(dir, "pop.wav"), seconds = 0.28) { t, duration ->
            // Bright rising chirp for a line clear.
            val sweep = 660.0 + 900.0 * (t / duration)
            val env = exp(-9.0 * t)
            sin(2 * PI * sweep * t) * env
        }
        val over = writeWav(File(dir, "over.wav"), seconds = 0.6) { t, duration ->
            // Falling two-tone for the end of the run.
            val sweep = 440.0 - 260.0 * (t / duration)
            val env = exp(-4.0 * t)
            (sin(2 * PI * sweep * t) * 0.75 + sin(2 * PI * sweep * 0.5 * t) * 0.25) * env
        }

        dropId = soundPool.load(drop.absolutePath, 1)
        popId = soundPool.load(pop.absolutePath, 1)
        overId = soundPool.load(over.absolutePath, 1)

        var loaded = 0
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0 && ++loaded == 3) ready = true
        }
        pool = soundPool
    }

    /** Piece landing on the board. */
    fun drop() = play(dropId, rate = 0.95f + 0.1f * Math.random().toFloat(), volume = 0.45f)

    /**
     * Line clear. The pitch climbs with [combo] and with how many lines went at once, which
     * is what makes a long streak feel like it is building.
     */
    fun clear(lines: Int, combo: Int) {
        val steps = (lines - 1).coerceIn(0, 3) + (combo - 1).coerceIn(0, 6)
        val rate = (1.0f + steps * 0.07f).coerceIn(0.5f, 2.0f)
        play(popId, rate = rate, volume = 0.7f)
    }

    fun gameOver() = play(overId, rate = 1f, volume = 0.6f)

    private fun play(soundId: Int, rate: Float, volume: Float) {
        if (!enabled || !ready || soundId == 0) return
        runCatching { pool?.play(soundId, volume, volume, 1, 0, rate) }
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        ready = false
    }

    /**
     * Renders [wave] into a 16-bit mono PCM WAV file and returns it.
     * [wave] receives the time in seconds and the total duration, and returns a sample in -1..1.
     */
    private fun writeWav(
        target: File,
        seconds: Double = 0.12,
        sampleRate: Int = 22_050,
        wave: (t: Double, duration: Double) -> Double,
    ): File {
        val frames = (seconds * sampleRate).toInt()
        val dataBytes = frames * 2
        val bytes = ByteArray(44 + dataBytes)

        fun putAscii(offset: Int, text: String) {
            for (i in text.indices) bytes[offset + i] = text[i].code.toByte()
        }

        fun putIntLe(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun putShortLe(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        putAscii(0, "RIFF")
        putIntLe(4, 36 + dataBytes)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putIntLe(16, 16)               // PCM header size
        putShortLe(20, 1)              // format: PCM
        putShortLe(22, 1)              // channels: mono
        putIntLe(24, sampleRate)
        putIntLe(28, sampleRate * 2)   // byte rate
        putShortLe(32, 2)              // block align
        putShortLe(34, 16)             // bits per sample
        putAscii(36, "data")
        putIntLe(40, dataBytes)

        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val sample = (wave(t, seconds).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            putShortLe(44 + i * 2, sample)
        }

        // Rewrite only when missing or a different length, so warm starts skip the work.
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            RandomAccessFile(target, "rw").use { file ->
                file.setLength(0)
                file.write(bytes)
            }
        }
        return target
    }

    private companion object {
        const val TAG = "SoundFx"
    }
}
