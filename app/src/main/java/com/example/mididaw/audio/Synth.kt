package com.example.mididaw.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

object NoteUtils {
    fun noteToFrequency(note: Int): Double =
        440.0 * 2.0.pow((note - 69) / 12.0)
}

/**
 * Проста ADSR-обвідна, щоб ноти не клацали на старті/кінці.
 * Часи в секундах.
 */
class Envelope(
    private val attackSec: Double = 0.01,
    private val decaySec: Double = 0.08,
    private val sustainLevel: Double = 0.7,
    private val releaseSec: Double = 0.12
) {
    /** tSec — час від початку ноти. noteDurSec — тривалість "натиснутої клавіші". */
    fun amplitudeAt(tSec: Double, noteDurSec: Double): Double {
        return when {
            tSec < 0 -> 0.0
            tSec < attackSec -> tSec / attackSec
            tSec < attackSec + decaySec ->
                1.0 - (1.0 - sustainLevel) * (tSec - attackSec) / decaySec
            tSec < noteDurSec -> sustainLevel
            tSec < noteDurSec + releaseSec ->
                sustainLevel * (1.0 - (tSec - noteDurSec) / releaseSec)
            else -> 0.0
        }
    }

    fun tailSec(): Double = releaseSec
}

enum class Waveform {
    SAWTOOTH, TRIANGLE, SINE, SQUARE;

    /** phase — фаза хвилі (не обов'язково обмежена [0,1), нормалізується всередині). */
    fun sample(phase: Double): Double = when (this) {
        SINE -> sin(2.0 * Math.PI * phase)
        SAWTOOTH -> 2.0 * (phase - floor(phase + 0.5))
        TRIANGLE -> {
            val p = phase - floor(phase)
            4.0 * abs(p - 0.5) - 1.0
        }
        SQUARE -> if (phase - floor(phase) < 0.5) 1.0 else -1.0
    }
}
