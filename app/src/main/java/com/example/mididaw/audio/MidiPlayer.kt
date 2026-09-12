package com.example.mididaw.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max
import kotlin.math.min

/**
 * Легкий саморобний синтезатор — лише для миттєвого звукового прев'ю
 * ноти при тапі/перетягуванні в редакторі (не для повного Play — той
 * іде через RealMidiPlayer/справжній .mid).
 */
class MidiPlayer(private val sampleRate: Int = 44100) {

    private val renderer = AudioRenderer(sampleRate)

    // Одне перевикористовуване STREAM-track для прев'ю — щоб швидкі
    // повторні тапи (наприклад, по ударних) не плодили нові AudioTrack
    // і не впирались у системний ліміт одночасних треків.
    private var previewTrack: AudioTrack? = null

    private fun toPcm16(pcmFloat: FloatArray): ShortArray =
        ShortArray(pcmFloat.size) { i ->
            val v = max(-1.0f, min(1.0f, pcmFloat[i]))
            (v * Short.MAX_VALUE).toInt().toShort()
        }

    private fun getOrCreatePreviewTrack(): AudioTrack {
        previewTrack?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, 1) * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        previewTrack = track
        return track
    }

    /**
     * Коротке прев'ю однієї ноти. Перевикористовує один і той самий
     * AudioTrack (зупиняє й перезаписує), тому може викликатись часто
     * поспіль без ризику вичерпати системні ресурси.
     */
    fun previewNote(note: Int, vel: Int = 100, durationMs: Long = 250) {
        val pcmFloat = renderer.renderSingleNote(note, vel, durationMs)
        if (pcmFloat.isEmpty()) return
        val pcm16 = toPcm16(pcmFloat)

        val track = getOrCreatePreviewTrack()
        try {
            track.pause()
            track.flush()
        } catch (_: Exception) {
        }
        try {
            track.play()
            track.write(pcm16, 0, pcm16.size)
        } catch (_: Exception) {
            // якщо трек раптом у поганому стані — перестворюємо на наступний виклик
            try { track.release() } catch (_: Exception) {}
            previewTrack = null
        }
    }

    fun stop() {
        // сумісність з попередніми викликами; основне звільнення — release()
    }

    /** Викликати з onDispose екрана. */
    fun release() {
        previewTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        previewTrack = null
    }
}
