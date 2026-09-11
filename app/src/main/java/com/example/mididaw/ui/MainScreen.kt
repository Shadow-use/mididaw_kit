package com.example.mididaw.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mididaw.audio.MidiPlayer
import com.example.mididaw.model.MidiJsonParser
import com.example.mididaw.model.MidiSong
import com.example.mididaw.model.MidiTrack
import kotlinx.coroutines.delay
import java.io.File

private enum class PlayState { STOPPED, PLAYING, PAUSED }

private fun shortenName(name: String, max: Int = 10): String =
    if (name.length > max) name.take(max) + "…" else name

@Composable
fun MainScreen(defaultAssetFileName: String = "jingle-bells.json") {
    val context = LocalContext.current
    val player = remember { MidiPlayer() }

    var song by remember { mutableStateOf<MidiSong?>(null) }
    var loadVersion by remember { mutableStateOf(0) }
    var availableAssets by remember { mutableStateOf<List<String>>(emptyList()) }

    var activeTrackIndex by remember { mutableStateOf<Int?>(null) } // null = "Усі"
    var playState by remember { mutableStateOf(PlayState.STOPPED) }
    var currentPlaybackTick by remember { mutableStateOf<Long?>(null) }

    var menuExpanded by remember { mutableStateOf(false) }
    var channelMenuExpanded by remember { mutableStateOf(false) }
    var instrumentMenuExpanded by remember { mutableStateOf(false) }

    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    fun loadSong(newSong: MidiSong) {
        song = newSong
        activeTrackIndex = null
        player.stop()
        playState = PlayState.STOPPED
        currentPlaybackTick = null
        loadVersion++
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    loadSong(MidiJsonParser.parse(text))
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Не вдалося відкрити файл: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        availableAssets = try {
            context.assets.list("")?.filter { it.endsWith(".json") } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        try {
            val jsonText = context.assets.open(defaultAssetFileName).bufferedReader().use { it.readText() }
            loadSong(MidiJsonParser.parse(jsonText))
        } catch (e: Exception) {
            Toast.makeText(context, "Не вдалося завантажити $defaultAssetFileName", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(playState) {
        while (playState == PlayState.PLAYING) {
            val s = song
            if (s != null && player.lastDurationMs > 0) {
                val posMs = player.currentPositionMs()
                if (posMs >= player.lastDurationMs) {
                    playState = PlayState.STOPPED
                    currentPlaybackTick = null
                    break
                }
                val msPerTick = 60000.0 / (s.bpm * s.ticksPerBeat)
                currentPlaybackTick = (posMs / msPerTick).toLong()
            }
            delay(30)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val s = song
        if (s == null) {
            Text("Завантаження...")
        } else {
            // Заголовок + темп
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(text = "${s.title}", modifier = Modifier.padding(end = 8.dp))
                Text(text = "BPM: ${s.bpm.roundToIntSafe()}", modifier = Modifier.padding(end = 4.dp))
                TinyIconBtn("−") {
                    song = s.copy(bpm = (s.bpm - 1).coerceAtLeast(20.0))
                }
                TinyIconBtn("+") {
                    song = s.copy(bpm = (s.bpm + 1).coerceAtMost(300.0))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                IconBtn(if (playState == PlayState.PLAYING) "⏸" else "▶") {
                    // якщо вибраний конкретний канал — граємо ТІЛЬКИ його
                    val songToPlay = activeTrackIndex?.let { idx ->
                        s.tracks.getOrNull(idx)?.let { t -> s.copy(tracks = listOf(t)) }
                    } ?: s

                    when (playState) {
                        PlayState.STOPPED -> {
                            player.play(songToPlay)
                            playState = PlayState.PLAYING
                        }
                        PlayState.PLAYING -> {
                            player.pause()
                            playState = PlayState.PAUSED
                        }
                        PlayState.PAUSED -> {
                            player.resume()
                            playState = PlayState.PLAYING
                        }
                    }
                }
                IconBtn("■") {
                    player.stop()
                    playState = PlayState.STOPPED
                    currentPlaybackTick = null
                }

                Box {
                    val label = activeTrackIndex?.let { s.tracks.getOrNull(it)?.name } ?: "Усі"
                    IconBtn(shortenName(label), wide = true) {
                        channelMenuExpanded = true
                    }
                    DropdownMenu(expanded = channelMenuExpanded, onDismissRequest = { channelMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Усі") }, onClick = {
                            activeTrackIndex = null
                            channelMenuExpanded = false
                        })
                        s.tracks.forEachIndexed { i, t ->
                            DropdownMenuItem(text = { Text(t.name) }, onClick = {
                                activeTrackIndex = i
                                channelMenuExpanded = false
                            })
                        }
                        DropdownMenuItem(text = { Text("+ Додати канал") }, onClick = {
                            val newTrack = MidiTrack(
                                name = "Track ${s.tracks.size + 1}",
                                channel = s.tracks.size,
                                program = 0,
                                events = emptyList()
                            )
                            val newIndex = s.tracks.size
                            song = s.copy(tracks = s.tracks + newTrack)
                            activeTrackIndex = newIndex
                            channelMenuExpanded = false
                        })
                        if (activeTrackIndex != null) {
                            DropdownMenuItem(text = { Text("✎ Перейменувати") }, onClick = {
                                renameText = s.tracks.getOrNull(activeTrackIndex!!)?.name ?: ""
                                renameDialogOpen = true
                                channelMenuExpanded = false
                            })
                        }
                    }
                }

                if (activeTrackIndex != null) {
                    Box {
                        val curProgram = s.tracks.getOrNull(activeTrackIndex!!)?.program ?: 0
                        IconBtn("${curProgram + 1}", wide = true) { instrumentMenuExpanded = true }
                        DropdownMenu(
                            expanded = instrumentMenuExpanded,
                            onDismissRequest = { instrumentMenuExpanded = false }
                        ) {
                            (1..128).forEach { p ->
                                DropdownMenuItem(text = { Text("$p") }, onClick = {
                                    val idx = activeTrackIndex
                                    if (idx != null) {
                                        val newTracks = s.tracks.mapIndexed { i, t ->
                                            if (i == idx) t.copy(program = p - 1) else t
                                        }
                                        song = s.copy(tracks = newTracks)
                                    }
                                    instrumentMenuExpanded = false
                                })
                            }
                        }
                    }
                }

                Box {
                    IconBtn("☰") { menuExpanded = true }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("📂 Відкрити файл...") }, onClick = {
                            menuExpanded = false
                            openDocLauncher.launch(arrayOf("*/*"))
                        })
                        DropdownMenuItem(text = { Text("— Приклади —") }, onClick = {}, enabled = false)
                        availableAssets.forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = {
                                menuExpanded = false
                                try {
                                    val text = context.assets.open(f).bufferedReader().use { it.readText() }
                                    loadSong(MidiJsonParser.parse(text))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            })
                        }
                        DropdownMenuItem(text = { Text("Зберегти") }, onClick = {
                            menuExpanded = false
                            val current = song
                            if (current != null) {
                                val json = MidiJsonParser.toJson(current)
                                val dir = context.getExternalFilesDir(null)
                                val safeTitle = current.title.ifBlank { "song" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                val file = File(dir, "${safeTitle}_${System.currentTimeMillis()}.json")
                                file.writeText(json)
                                Toast.makeText(context, "Збережено: ${file.name}", Toast.LENGTH_LONG).show()
                            }
                        })
                        DropdownMenuItem(text = { Text("Вихід") }, onClick = {
                            menuExpanded = false
                            (context as? Activity)?.finish()
                        })
                    }
                }
            }

            EditablePianoRollView(
                initialSong = s,
                loadKey = loadVersion,
                activeTrackIndex = activeTrackIndex,
                currentPlaybackTick = currentPlaybackTick,
                onSongChanged = { updated -> song = updated },
                onActiveTrackChangeForNewNotes = { idx -> activeTrackIndex = idx },
                onPreviewNote = { note, durMs -> player.previewNote(note, durationMs = durMs) },
                modifier = Modifier.fillMaxSize()
            )

            if (renameDialogOpen) {
                AlertDialog(
                    onDismissRequest = { renameDialogOpen = false },
                    title = { Text("Назва каналу") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val idx = activeTrackIndex
                            val cur = song
                            if (idx != null && cur != null && renameText.isNotBlank()) {
                                val newTracks = cur.tracks.mapIndexed { i, t ->
                                    if (i == idx) t.copy(name = renameText) else t
                                }
                                song = cur.copy(tracks = newTracks)
                            }
                            renameDialogOpen = false
                        }) { Text("Зберегти") }
                    },
                    dismissButton = {
                        Button(onClick = { renameDialogOpen = false }) { Text("Скасувати") }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }
}

private fun Double.roundToIntSafe(): Int = Math.round(this).toInt()

@Composable
private fun IconBtn(label: String, wide: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = if (wide) 10.dp else 6.dp, vertical = 6.dp),
        modifier = Modifier.padding(end = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1))
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun TinyIconBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.padding(end = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1))
    ) {
        Text(label, color = Color.White)
    }
}
