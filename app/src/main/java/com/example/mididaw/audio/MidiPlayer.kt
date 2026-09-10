package com.example.mididaw.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.mididaw.model.MidiSong
import kotlin.math.max
import kotlin.math.min

class MidiPlayer(private val sampleRate: Int = 44100) {

    private var audioTrack: AudioTrack? = null

    /** Тривалість останньо відрендереної пісні в мілісекундах. */
    var lastDurationMs: Long = 0
        private set

    private val renderer = AudioRenderer(sampleRate)

    private fun buildAudioTrack(pcm16: ShortArray): AudioTrack {
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
            .setBufferSizeInBytes(pcm16.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm16, 0, pcm16.size)
        return track
    }

    private fun toPcm16(pcmFloat: FloatArray): ShortArray =
        ShortArray(pcmFloat.size) { i ->
            val v = max(-1.0f, min(1.0f, pcmFloat[i]))
            (v * Short.MAX_VALUE).toInt().toShort()
        }

    /** Рендерить і одразу програє пісню (або відфільтрований трек) з початку. */
    fun play(song: MidiSong) {
        stop()

        val pcmFloat = renderer.render(song)
        lastDurationMs = (pcmFloat.size.toLong() * 1000L) / sampleRate

        val track = buildAudioTrack(toPcm16(pcmFloat))
        track.play()
        audioTrack = track
    }

    /** Призупиняє без втрати позиції (працює завдяки MODE_STATIC). */
    fun pause() {
        audioTrack?.let {
            try {
                it.pause()
            } catch (_: IllegalStateException) {
            }
        }
    }

    /** Продовжує з місця, де зупинились. */
    fun resume() {
        audioTrack?.let {
            try {
                it.play()
            } catch (_: IllegalStateException) {
            }
        }
    }

    fun stop() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        audioTrack = null
        lastDurationMs = 0
    }

    fun isPlaying(): Boolean =
        audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /** Поточна позиція відтворення в мілісекундах від початку. */
    fun currentPositionMs(): Long {
        val track = audioTrack ?: return 0L
        val frames = track.playbackHeadPosition.toLong()
        return (frames * 1000L) / sampleRate
    }

    /**
     * Коротке "fire and forget" прев'ю однієї ноти — не чіпає основний
     * audioTrack (грає паралельно, не зупиняючи/не заважаючи Play).
     */
    fun previewNote(note: Int, vel: Int = 100, durationMs: Long = 250) {
        val pcmFloat = renderer.renderSingleNote(note, vel, durationMs)
        if (pcmFloat.isEmpty()) return
        val pcm16 = toPcm16(pcmFloat)
        val previewTrack = buildAudioTrack(pcm16)
        previewTrack.play()
        Thread {
            try {
                Thread.sleep(durationMs + 400)
            } catch (_: InterruptedException) {
            }
            try {
                previewTrack.stop()
            } catch (_: Exception) {
            }
            previewTrack.release()
        }.start()
    }
}
