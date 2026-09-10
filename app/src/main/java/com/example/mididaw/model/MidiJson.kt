package com.example.mididaw.model

import org.json.JSONArray
import org.json.JSONObject

data class MidiEvent(
    val note: Int,
    val start: Long,   // абсолютний час у ticks
    val dur: Long,      // тривалість у ticks
    val vel: Int
)

data class MidiTrack(
    val name: String,
    val channel: Int,
    val program: Int,
    val events: List<MidiEvent>
)

data class MidiSong(
    val title: String,
    val bpm: Double,
    val ticksPerBeat: Int,
    val tracks: List<MidiTrack>
)

/**
 * Парсер підтримує всі три формати з твого JSON_MIDI_Format_Instruction.md:
 *  1) Рекомендований формат (bpm / ticks_per_beat / tracks / events / note / start / dur / vel)
 *  2) Gemini-формат (header.tempo / notes / pitch / ticks / duration / velocity)
 *  3) Старий delay-формат (delay замість start)
 */
object MidiJsonParser {

    fun parse(jsonText: String): MidiSong {
        val root = JSONObject(jsonText)
        val isGeminiFormat = root.has("header")

        val title: String
        val bpm: Double
        val ticksPerBeat: Int

        if (isGeminiFormat) {
            val header = root.getJSONObject("header")
            title = header.optString("title", "Untitled")
            bpm = header.optDouble("tempo", 90.0)
            ticksPerBeat = header.optInt("ticks_per_beat", 480)
        } else {
            title = root.optString("title", "Untitled")
            bpm = root.optDouble("bpm", 90.0)
            ticksPerBeat = root.optInt("ticks_per_beat", 480)
        }

        val tracksJson = root.getJSONArray("tracks")
        val tracks = mutableListOf<MidiTrack>()

        for (i in 0 until tracksJson.length()) {
            val trackJson = tracksJson.getJSONObject(i)
            val name = trackJson.optString("name", "Track ${i + 1}")
            val channel = trackJson.optInt("channel", 0)
            val program = trackJson.optInt("program", 0)

            val events = mutableListOf<MidiEvent>()

            if (isGeminiFormat) {
                val notesJson = trackJson.optJSONArray("notes") ?: JSONArray()
                for (j in 0 until notesJson.length()) {
                    val n = notesJson.getJSONObject(j)
                    events.add(
                        MidiEvent(
                            note = n.getInt("pitch"),
                            start = n.optLong("ticks", 0),
                            dur = n.optLong("duration", ticksPerBeat.toLong()),
                            vel = n.optInt("velocity", 90)
                        )
                    )
                }
            } else {
                val eventsJson = trackJson.optJSONArray("events") ?: JSONArray()
                var runningStart = 0L
                for (j in 0 until eventsJson.length()) {
                    val e = eventsJson.getJSONObject(j)
                    val start = if (e.has("start")) {
                        e.getLong("start")
                    } else {
                        // старий delay-формат: зсув відносно попередньої ноти
                        runningStart += e.optLong("delay", 0)
                        runningStart
                    }
                    events.add(
                        MidiEvent(
                            note = e.getInt("note"),
                            start = start,
                            dur = e.optLong("dur", ticksPerBeat.toLong()),
                            vel = e.optInt("vel", 90)
                        )
                    )
                }
            }

            tracks.add(MidiTrack(name, channel, program, events))
        }

        return MidiSong(title, bpm, ticksPerBeat, tracks)
    }

    /** Серіалізація назад у "рекомендований формат" — для збереження після редагування. */
    fun toJson(song: MidiSong): String {
        val root = JSONObject()
        root.put("title", song.title)
        root.put("bpm", song.bpm)
        root.put("ticks_per_beat", song.ticksPerBeat)

        val tracksArr = JSONArray()
        for (track in song.tracks) {
            val trackObj = JSONObject()
            trackObj.put("name", track.name)
            trackObj.put("channel", track.channel)
            trackObj.put("program", track.program)

            val eventsArr = JSONArray()
            for (e in track.events) {
                val eventObj = JSONObject()
                eventObj.put("note", e.note)
                eventObj.put("start", e.start)
                eventObj.put("dur", e.dur)
                eventObj.put("vel", e.vel)
                eventsArr.put(eventObj)
            }
            trackObj.put("events", eventsArr)
            tracksArr.put(trackObj)
        }
        root.put("tracks", tracksArr)

        return root.toString(2)
    }
}
