package com.example.mididaw.audio

import android.content.Context
import android.media.MediaPlayer
import com.example.mididaw.midi.MidiFileWriter
import com.example.mididaw.model.MidiSong
import java.io.File

/**
 * Грає СПРАВЖНІЙ .mid через вбудований у Android MIDI-синтезатор
 * (Sonivox EAS) — реальні GM-інструменти по program/channel.
 */
class RealMidiPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun play(song: MidiSong) {
        stop()

        val bytes = MidiFileWriter.write(song)
        val file = File(context.cacheDir, "mididaw_preview_${System.currentTimeMillis()}.mid")
        file.writeBytes(bytes)
        tempFile = file

        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.setVolume(1f, 1f)
        mp.prepare()
        mp.start()
        mediaPlayer = mp
    }

    fun pause() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun stop() {
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun currentPositionMs(): Long = (mediaPlayer?.currentPosition ?: 0).toLong()

    fun durationMs(): Long = (mediaPlayer?.duration ?: 0).toLong()

    /** Записує поточну пісню у .mid і повертає шлях до файлу (для "Експортувати"). */
    fun exportToFile(song: MidiSong, destination: File): File {
        val bytes = MidiFileWriter.write(song)
        destination.writeBytes(bytes)
        return destination
    }
}
