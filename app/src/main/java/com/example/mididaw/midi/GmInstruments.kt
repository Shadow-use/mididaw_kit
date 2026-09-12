package com.example.mididaw.midi

/** General MIDI (GM1) — 128 інструментів + мапа ударних для каналу 10. */
object GmInstruments {

    /** Назва категорії кожної групи з 8 інструментів (16 категорій × 8 = 128). */
    val CATEGORIES = listOf(
        "Piano", "Chromatic Percussion", "Organ", "Guitar",
        "Bass", "Strings", "Ensemble", "Brass",
        "Reed", "Pipe", "Synth Lead", "Synth Pad",
        "Synth Effects", "Ethnic", "Percussive", "Sound Effects"
    )

    /** Індекс 0 = program 1 (Acoustic Grand Piano) ... індекс 127 = program 128 (Gunshot). */
    val NAMES = listOf(
        "Acoustic Grand Piano", "Bright Acoustic Piano", "Electric Grand Piano", "Honky-tonk Piano",
        "Electric Piano 1", "Electric Piano 2", "Harpsichord", "Clavinet",
        "Celesta", "Glockenspiel", "Music Box", "Vibraphone",
        "Marimba", "Xylophone", "Tubular Bells", "Dulcimer",
        "Drawbar Organ", "Percussive Organ", "Rock Organ", "Church Organ",
        "Reed Organ", "Accordion", "Harmonica", "Tango Accordion",
        "Acoustic Guitar (nylon)", "Acoustic Guitar (steel)", "Electric Guitar (jazz)", "Electric Guitar (clean)",
        "Electric Guitar (muted)", "Overdriven Guitar", "Distortion Guitar", "Guitar Harmonics",
        "Acoustic Bass", "Electric Bass (finger)", "Electric Bass (pick)", "Fretless Bass",
        "Slap Bass 1", "Slap Bass 2", "Synth Bass 1", "Synth Bass 2",
        "Violin", "Viola", "Cello", "Contrabass",
        "Tremolo Strings", "Pizzicato Strings", "Orchestral Harp", "Timpani",
        "String Ensemble 1", "String Ensemble 2", "Synth Strings 1", "Synth Strings 2",
        "Choir Aahs", "Voice Oohs", "Synth Voice", "Orchestral Hit",
        "Trumpet", "Trombone", "Tuba", "Muted Trumpet",
        "French Horn", "Brass Section", "Synth Brass 1", "Synth Brass 2",
        "Soprano Sax", "Alto Sax", "Tenor Sax", "Baritone Sax",
        "Oboe", "English Horn", "Bassoon", "Clarinet",
        "Piccolo", "Flute", "Recorder", "Pan Flute",
        "Blown Bottle", "Shakuhachi", "Whistle", "Ocarina",
        "Lead 1 (square)", "Lead 2 (sawtooth)", "Lead 3 (calliope)", "Lead 4 (chiff)",
        "Lead 5 (charang)", "Lead 6 (voice)", "Lead 7 (fifths)", "Lead 8 (bass+lead)",
        "Pad 1 (new age)", "Pad 2 (warm)", "Pad 3 (polysynth)", "Pad 4 (choir)",
        "Pad 5 (bowed)", "Pad 6 (metallic)", "Pad 7 (halo)", "Pad 8 (sweep)",
        "FX 1 (rain)", "FX 2 (soundtrack)", "FX 3 (crystal)", "FX 4 (atmosphere)",
        "FX 5 (brightness)", "FX 6 (goblins)", "FX 7 (echoes)", "FX 8 (sci-fi)",
        "Sitar", "Banjo", "Shamisen", "Koto",
        "Kalimba", "Bagpipe", "Fiddle", "Shanai",
        "Tinkle Bell", "Agogo", "Steel Drums", "Woodblock",
        "Taiko Drum", "Melodic Tom", "Synth Drum", "Reverse Cymbal",
        "Guitar Fret Noise", "Breath Noise", "Seashore", "Bird Tweet",
        "Telephone Ring", "Helicopter", "Applause", "Gunshot"
    )

    /** MIDI-канал ударних (0-indexed, тобто "канал 10" у людській нумерації 1-16). */
    const val DRUM_CHANNEL = 9

    /** Стандартна GM-мапа ударних нот для каналу 10. */
    val DRUM_NAMES = mapOf(
        35 to "Acoustic Bass Drum", 36 to "Bass Drum 1", 37 to "Side Stick", 38 to "Acoustic Snare",
        39 to "Hand Clap", 40 to "Electric Snare", 41 to "Low Floor Tom", 42 to "Closed Hi-Hat",
        43 to "High Floor Tom", 44 to "Pedal Hi-Hat", 45 to "Low Tom", 46 to "Open Hi-Hat",
        47 to "Low-Mid Tom", 48 to "Hi-Mid Tom", 49 to "Crash Cymbal 1", 50 to "High Tom",
        51 to "Ride Cymbal 1", 52 to "Chinese Cymbal", 53 to "Ride Bell", 54 to "Tambourine",
        55 to "Splash Cymbal", 56 to "Cowbell", 57 to "Crash Cymbal 2", 58 to "Vibraslap",
        59 to "Ride Cymbal 2", 60 to "Hi Bongo", 61 to "Low Bongo", 62 to "Mute Hi Conga",
        63 to "Open Hi Conga", 64 to "Low Conga", 65 to "High Timbale", 66 to "Low Timbale",
        67 to "High Agogo", 68 to "Low Agogo", 69 to "Cabasa", 70 to "Maracas",
        71 to "Short Whistle", 72 to "Long Whistle", 73 to "Short Guiro", 74 to "Long Guiro",
        75 to "Claves", 76 to "Hi Wood Block", 77 to "Low Wood Block", 78 to "Mute Cuica",
        79 to "Open Cuica", 80 to "Mute Triangle", 81 to "Open Triangle"
    )
}
