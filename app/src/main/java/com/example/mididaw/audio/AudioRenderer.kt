package com.example.mididaw.audio

import com.example.mididaw.model.MidiSong
import kotlin.math.abs
import kotlin.math.max

class AudioRenderer(
    private val sampleRate: Int = 44100,
    private val waveform: Waveform = Waveform.TRIANGLE
) {
    private val envelope = Envelope()

    /**
     * Рендерить усю пісню (всі треки змішані в одну доріжку) у float-масив [-1, 1].
     * Простий offline-рендер — годиться для коротких мелодій (кілька хвилин).
     */
    fun render(song: MidiSong): FloatArray {
        val msPerTick = 60000.0 / (song.bpm * song.ticksPerBeat)

        data class FlatNote(val startSec: Double, val durSec: Double, val note: Int, val vel: Int)

        val flatEvents = mutableListOf<FlatNote>()
        var maxEndSec = 0.1

        for (track in song.tracks) {
            for (e in track.events) {
                val startSec = (e.start * msPerTick) / 1000.0
                val durSec = (e.dur * msPerTick) / 1000.0
                flatEvents.add(FlatNote(startSec, durSec, e.note, e.vel))
                maxEndSec = max(maxEndSec, startSec + durSec + envelope.tailSec())
            }
        }

        val totalSamples = (maxEndSec * sampleRate).toInt() + 1
        val buffer = FloatArray(totalSamples)

        for (n in flatEvents) {
            val freq = NoteUtils.noteToFrequency(n.note)
            val gain = (n.vel / 127.0) * 0.3 // запас, щоб уникнути кліпінгу при накладенні нот

            val startSample = (n.startSec * sampleRate).toInt()
            val noteTotalSamples = ((n.durSec + envelope.tailSec()) * sampleRate).toInt()

            var phase = 0.0
            val phaseInc = freq / sampleRate

            for (i in 0 until noteTotalSamples) {
                val sampleIdx = startSample + i
                if (sampleIdx >= totalSamples) break

                val tSec = i.toDouble() / sampleRate
                val amp = envelope.amplitudeAt(tSec, n.durSec)
                if (amp > 0.0) {
                    val raw = waveform.sample(phase)
                    buffer[sampleIdx] += (raw * amp * gain).toFloat()
                }
                phase += phaseInc
            }
        }

        // м'який лімітер — якщо багато нот одночасно, не даємо звуку "тріщати"
        var maxAbs = 0.0001f
        for (s in buffer) maxAbs = max(maxAbs, abs(s))
        if (maxAbs > 1.0f) {
            val scale = 0.98f / maxAbs
            for (i in buffer.indices) buffer[i] = buffer[i] * scale
        }

        return buffer
    }
}
