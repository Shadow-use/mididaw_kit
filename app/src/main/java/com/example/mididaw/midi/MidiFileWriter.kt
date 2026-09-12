package com.example.mididaw.midi

import com.example.mididaw.model.MidiSong
import com.example.mididaw.model.MidiTrack
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Пише Standard MIDI File (формат 1, багатотрековий) прямо з MidiSong —
 * без mido, без Termux, без зовнішніх бібліотек.
 */
object MidiFileWriter {

    fun write(song: MidiSong): ByteArray {
        val out = ByteArrayOutputStream()
        val division = song.ticksPerBeat.coerceIn(1, 0x7FFF)

        val trackChunks = mutableListOf<ByteArray>()
        trackChunks.add(buildTempoTrack(song.bpm))
        for (track in song.tracks) {
            trackChunks.add(buildInstrumentTrack(track))
        }

        writeHeader(out, format = 1, ntrks = trackChunks.size, division = division)
        trackChunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun writeHeader(out: ByteArrayOutputStream, format: Int, ntrks: Int, division: Int) {
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeUInt32(out, 6)
        writeUInt16(out, format)
        writeUInt16(out, ntrks)
        writeUInt16(out, division)
    }

    private fun buildTempoTrack(bpm: Double): ByteArray {
        val body = ByteArrayOutputStream()
        val microsPerQuarter = (60_000_000.0 / bpm).toInt().coerceIn(1, 0xFFFFFF)

        writeVarLen(body, 0)
        body.write(0xFF); body.write(0x51); body.write(0x03)
        body.write((microsPerQuarter shr 16) and 0xFF)
        body.write((microsPerQuarter shr 8) and 0xFF)
        body.write(microsPerQuarter and 0xFF)

        writeVarLen(body, 0)
        body.write(0xFF); body.write(0x2F); body.write(0x00)

        return wrapTrackChunk(body.toByteArray())
    }

    private data class Ev(val tick: Long, val isNoteOn: Boolean, val note: Int, val vel: Int)

    /** Піднімає гучність ноти, зберігаючи відносну динаміку. */
    private fun boostVelocity(vel: Int): Int =
        (vel.coerceIn(1, 127) * 1.5).roundToInt().coerceIn(1, 127)

    private fun buildInstrumentTrack(track: MidiTrack): ByteArray {
        val body = ByteArrayOutputStream()
        val channel = ((track.channel % 16) + 16) % 16

        val events = mutableListOf<Ev>()
        for (e in track.events) {
            val note = e.note.coerceIn(0, 127)
            val vel = boostVelocity(e.vel)
            events.add(Ev(e.start, true, note, vel))
            events.add(Ev(e.start + e.dur, false, note, 0))
        }
        // note-off раніше за note-on на тому самому тику — щоб уникнути "залипання"
        events.sortWith(compareBy({ it.tick }, { if (it.isNoteOn) 1 else 0 }))

        var lastTick = 0L

        val nameBytes = track.name.toByteArray(Charsets.US_ASCII)
        writeVarLen(body, 0)
        body.write(0xFF); body.write(0x03); writeVarLen(body, nameBytes.size.toLong()); body.write(nameBytes)

        writeVarLen(body, 0)
        body.write(0xC0 or channel)
        body.write(track.program.coerceIn(0, 127))

        // Гучність каналу на максимум (CC7 = Channel Volume, CC11 = Expression) —
        // за замовчуванням деякі синтезатори ставлять їх на середину (~100/127),
        // через що мелодія звучить тихо.
        writeVarLen(body, 0)
        body.write(0xB0 or channel); body.write(7); body.write(127)
        writeVarLen(body, 0)
        body.write(0xB0 or channel); body.write(11); body.write(127)

        for (ev in events) {
            val delta = (ev.tick - lastTick).coerceAtLeast(0)
            writeVarLen(body, delta)
            lastTick = ev.tick
            if (ev.isNoteOn) {
                body.write(0x90 or channel)
                body.write(ev.note)
                body.write(ev.vel)
            } else {
                body.write(0x80 or channel)
                body.write(ev.note)
                body.write(0)
            }
        }

        writeVarLen(body, 0)
        body.write(0xFF); body.write(0x2F); body.write(0x00)

        return wrapTrackChunk(body.toByteArray())
    }

    private fun wrapTrackChunk(body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeUInt32(out, body.size)
        out.write(body)
        return out.toByteArray()
    }

    private fun writeUInt32(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeUInt16(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeVarLen(out: ByteArrayOutputStream, valueIn: Long) {
        var value = valueIn.coerceAtLeast(0)
        val stack = mutableListOf<Long>()
        stack.add(value and 0x7F)
        value = value shr 7
        while (value > 0) {
            stack.add((value and 0x7F) or 0x80)
            value = value shr 7
        }
        for (i in stack.indices.reversed()) {
            out.write(stack[i].toInt())
        }
    }
}
