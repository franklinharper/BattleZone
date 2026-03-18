package com.franklinharper.battlezone

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

object DesktopGameSoundPlayer {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, AUDIO_THREAD_NAME).apply { isDaemon = true }
    }

    fun handleEvent(event: GameEvent) {
        val pattern = when (event) {
            is GameEvent.AttackExecuted -> {
                if (event.result.attackerWins) {
                    ATTACK_SUCCESS_PATTERN
                } else {
                    ATTACK_FAIL_PATTERN
                }
            }
            is GameEvent.GameEnded -> GAME_WON_PATTERN
            else -> null
        } ?: return

        executor.execute {
            playPattern(pattern)
        }
    }

    private fun playPattern(pattern: List<Tone>) {
        for (tone in pattern) {
            playTone(tone.frequencyHz, tone.durationMs)
            if (tone.pauseAfterMs > 0) {
                sleep(tone.pauseAfterMs)
            }
        }
    }

    private fun playTone(frequencyHz: Int, durationMs: Int) {
        val sampleCount = (SAMPLE_RATE * durationMs) / MILLIS_PER_SECOND
        val byteData = ByteArray(sampleCount * BYTES_PER_SAMPLE)
        var index = 0
        for (sampleIndex in 0 until sampleCount) {
            val angle = TWO_PI * sampleIndex * frequencyHz / SAMPLE_RATE.toDouble()
            val sampleValue = (sin(angle) * MAX_AMPLITUDE).toInt().toShort()
            byteData[index++] = (sampleValue.toInt() and LOW_BYTE_MASK).toByte()
            byteData[index++] = ((sampleValue.toInt() shr BYTE_BITS) and LOW_BYTE_MASK).toByte()
        }

        try {
            val format = AudioFormat(
                SAMPLE_RATE.toFloat(),
                BITS_PER_SAMPLE,
                CHANNEL_COUNT,
                SIGNED_PCM,
                LITTLE_ENDIAN
            )
            AudioSystem.getSourceDataLine(format).use { line ->
                line.open(format)
                line.start()
                line.write(byteData, 0, byteData.size)
                line.drain()
            }
        } catch (_: Exception) {
            // Sound playback is best-effort and should never break game flow.
        }
    }

    private fun sleep(durationMs: Int) {
        try {
            TimeUnit.MILLISECONDS.sleep(durationMs.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private data class Tone(
        val frequencyHz: Int,
        val durationMs: Int,
        val pauseAfterMs: Int = 0
    )

    private const val AUDIO_THREAD_NAME = "battlezone-audio"
    private const val SAMPLE_RATE = 44_100
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNEL_COUNT = 1
    private const val BYTES_PER_SAMPLE = 2
    private const val MILLIS_PER_SECOND = 1_000
    private const val MAX_AMPLITUDE = 12_000
    private const val BYTE_BITS = 8
    private const val LOW_BYTE_MASK = 0xFF
    private const val TWO_PI = 2.0 * PI
    private const val SIGNED_PCM = true
    private const val LITTLE_ENDIAN = false

    private const val ATTACK_SUCCESS_LOW_HZ = 540
    private const val ATTACK_SUCCESS_HIGH_HZ = 780
    private const val ATTACK_FAIL_HZ = 260
    private const val GAME_WON_FIRST_HZ = 660
    private const val GAME_WON_SECOND_HZ = 880
    private const val GAME_WON_THIRD_HZ = 1_050
    private const val SHORT_TONE_MS = 90
    private const val MEDIUM_TONE_MS = 120
    private const val LONG_TONE_MS = 200
    private const val GAP_SHORT_MS = 35
    private const val GAP_MEDIUM_MS = 50

    private val ATTACK_SUCCESS_PATTERN = listOf(
        Tone(ATTACK_SUCCESS_LOW_HZ, SHORT_TONE_MS, GAP_SHORT_MS),
        Tone(ATTACK_SUCCESS_HIGH_HZ, SHORT_TONE_MS, 0)
    )
    private val ATTACK_FAIL_PATTERN = listOf(
        Tone(ATTACK_FAIL_HZ, MEDIUM_TONE_MS, 0)
    )
    private val GAME_WON_PATTERN = listOf(
        Tone(GAME_WON_FIRST_HZ, SHORT_TONE_MS, GAP_MEDIUM_MS),
        Tone(GAME_WON_SECOND_HZ, SHORT_TONE_MS, GAP_MEDIUM_MS),
        Tone(GAME_WON_THIRD_HZ, LONG_TONE_MS, 0)
    )
}
