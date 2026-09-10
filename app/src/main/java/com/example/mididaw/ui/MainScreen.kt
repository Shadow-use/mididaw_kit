package com.example.mididaw.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mididaw.audio.MidiPlayer
import com.example.mididaw.model.MidiJsonParser
import com.example.mididaw.model.MidiSong

/**
 * Демонстраційний екран.
 * Поклади свій jingle-bells.json (або будь-який інший з твого архіву)
 * у app/src/main/assets/ — і він завантажиться й програється тут.
 */
@Composable
fun MainScreen(assetFileName: String = "jingle-bells.json") {
    val context = LocalContext.current
    var song by remember { mutableStateOf<MidiSong?>(null) }
    val player = remember { MidiPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(assetFileName) {
        val jsonText = context.assets.open(assetFileName)
            .bufferedReader()
            .use { it.readText() }
        song = MidiJsonParser.parse(jsonText)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val s = song
        if (s == null) {
            Text("Завантаження...")
        } else {
            Text(text = "${s.title}  |  BPM: ${s.bpm}")

            Button(onClick = {
                player.play(s)
                isPlaying = true
            }) {
                Text(if (isPlaying) "Грає…" else "▶ Play")
            }

            Button(onClick = {
                player.stop()
                isPlaying = false
            }) {
                Text("■ Stop")
            }

            PianoRollView(
                song = s,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }
}
