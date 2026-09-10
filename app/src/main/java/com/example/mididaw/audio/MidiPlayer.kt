package com.example.mididaw.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.mididaw.model.MidiSong
import kotlin.math.max
import kotlin.math.min

class MidiPlayer(private val sampleRate: Int = 44100) {

    private var audioTrack: AudioTrack? = null
    private val renderer = AudioRenderer(sampleRate)

    /** Рендерить і одразу програє пісню. */
    fun play(song: MidiSong) {
        stop()

        val pcmFloat = renderer.render(song)
        val pcm16 = ShortArray(pcmFloat.size) { i ->
            val v = max(-1.0f, min(1.0f, pcmFloat[i]))
            (v * Short.MAX_VALUE).toInt().toShort()
        }

        val bufferSizeBytes = pcm16.size * 2

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
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm16, 0, pcm16.size)
        track.play()
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // трек ще не грав — ігноруємо
            }
            it.release()
        }
        audioTrack = null
    }

    fun isPlaying(): Boolean =
        audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
}
