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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mididaw.audio.MidiPlayer
import com.example.mididaw.audio.RealMidiPlayer
import com.example.mididaw.midi.GmInstruments
import com.example.mididaw.model.MidiJsonParser
import com.example.mididaw.model.MidiSong
import com.example.mididaw.model.MidiTrack
import kotlinx.coroutines.delay
import java.io.File

private enum class PlayState { STOPPED, PLAYING, PAUSED }

private const val MAX_HISTORY = 50

private fun shortenName(name: String, max: Int = 10): String =
    if (name.length > max) name.take(max) + "…" else name

@Composable
fun MainScreen(defaultAssetFileName: String = "jingle-bells.json") {
    val context = LocalContext.current

    val previewPlayer = remember { MidiPlayer() }
    val realPlayer = remember { RealMidiPlayer(context) }

    var song by remember { mutableStateOf<MidiSong?>(null) }
    var loadVersion by remember { mutableStateOf(0) }
    var availableAssets by remember { mutableStateOf<List<String>>(emptyList()) }

    // Undo/Redo — стек попередніх/наступних станів усієї пісні
    // (ноти, темп, канали, інструменти — все разом).
    val undoStack = remember { mutableStateListOf<MidiSong>() }
    val redoStack = remember { mutableStateListOf<MidiSong>() }

    var activeTrackIndex by remember { mutableStateOf<Int?>(null) } // null = "Усі"
    var playState by remember { mutableStateOf(PlayState.STOPPED) }
    var currentPlaybackTick by remember { mutableStateOf<Long?>(null) }

    var menuExpanded by remember { mutableStateOf(false) }
    var channelMenuExpanded by remember { mutableStateOf(false) }
    var instrumentMenuExpanded by remember { mutableStateOf(false) }

    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    fun clampActiveTrack(s: MidiSong) {
        val idx = activeTrackIndex
        if (idx != null && idx >= s.tracks.size) activeTrackIndex = null
    }

    /** Будь-яка зміна пісні користувачем (ноти, темп, канали...) іде через це — пушить попередній стан в undo. */
    fun updateSong(newSong: MidiSong) {
        val cur = song
        if (cur != null) {
            undoStack.add(cur)
            if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        }
        redoStack.clear()
        song = newSong
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val cur = song
        if (cur != null) redoStack.add(cur)
        val restored = undoStack.removeAt(undoStack.lastIndex)
        song = restored
        clampActiveTrack(restored)
        loadVersion++ // змушує піано-рол перечитати ноти зі стану, що повернули
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val cur = song
        if (cur != null) undoStack.add(cur)
        val restored = redoStack.removeAt(redoStack.lastIndex)
        song = restored
        clampActiveTrack(restored)
        loadVersion++
    }

    fun loadSong(newSong: MidiSong) {
        song = newSong
        undoStack.clear()
        redoStack.clear()
        activeTrackIndex = null
        realPlayer.stop()
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
            if (!realPlayer.isPlaying()) {
                playState = PlayState.STOPPED
                currentPlaybackTick = null
                break
            }
            val s = song
            if (s != null) {
                val msPerTick = 60000.0 / (s.bpm * s.ticksPerBeat)
                currentPlaybackTick = (realPlayer.currentPositionMs() / msPerTick).toLong()
            }
            delay(30)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val s = song
        if (s == null) {
            Text("Завантаження...")
        } else {
            val isDrumChannel = activeTrackIndex
                ?.let { s.tracks.getOrNull(it)?.channel == GmInstruments.DRUM_CHANNEL } ?: false
            val drumTrackIndices = s.tracks.withIndex()
                .filter { it.value.channel == GmInstruments.DRUM_CHANNEL }
                .map { it.index }
                .toSet()

            // Заголовок + темп + undo/redo
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
                Text(text = "${s.title}", modifier = Modifier.padding(end = 8.dp))
                Text(text = "BPM: ${s.bpm.roundToIntSafe()}", modifier = Modifier.padding(end = 4.dp))
                TinyIconBtn("−") { updateSong(s.copy(bpm = (s.bpm - 1).coerceAtLeast(20.0))) }
                TinyIconBtn("+") { updateSong(s.copy(bpm = (s.bpm + 1).coerceAtMost(300.0))) }
                TinyIconBtn("↶", enabled = undoStack.isNotEmpty()) { undo() }
                TinyIconBtn("↷", enabled = redoStack.isNotEmpty()) { redo() }
            }

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                IconBtn(if (playState == PlayState.PLAYING) "⏸" else "▶") {
                    val songToPlay = activeTrackIndex?.let { idx ->
                        s.tracks.getOrNull(idx)?.let { t -> s.copy(tracks = listOf(t)) }
                    } ?: s

                    when (playState) {
                        PlayState.STOPPED -> {
                            realPlayer.play(songToPlay)
                            playState = PlayState.PLAYING
                        }
                        PlayState.PLAYING -> {
                            realPlayer.pause()
                            playState = PlayState.PAUSED
                        }
                        PlayState.PAUSED -> {
                            realPlayer.resume()
                            playState = PlayState.PLAYING
                        }
                    }
                }
                IconBtn("■") {
                    realPlayer.stop()
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
                            val suffix = if (t.channel == GmInstruments.DRUM_CHANNEL) " 🥁" else ""
                            DropdownMenuItem(text = { Text(t.name + suffix) }, onClick = {
                                activeTrackIndex = i
                                channelMenuExpanded = false
                            })
                        }
                        DropdownMenuItem(text = { Text("+ Додати канал") }, onClick = {
                            val newTrack = MidiTrack(
                                name = "Track ${s.tracks.size + 1}",
                                channel = s.tracks.count { it.channel != GmInstruments.DRUM_CHANNEL },
                                program = 0,
                                events = emptyList()
                            )
                            val newIndex = s.tracks.size
                            updateSong(s.copy(tracks = s.tracks + newTrack))
                            activeTrackIndex = newIndex
                            channelMenuExpanded = false
                        })
                        if (drumTrackIndices.isEmpty()) {
                            DropdownMenuItem(text = { Text("+ Додати ударні (канал 10) 🥁") }, onClick = {
                                val drumTrack = MidiTrack(
                                    name = "Drums",
                                    channel = GmInstruments.DRUM_CHANNEL,
                                    program = 0,
                                    events = emptyList()
                                )
                                val newIndex = s.tracks.size
                                updateSong(s.copy(tracks = s.tracks + drumTrack))
                                activeTrackIndex = newIndex
                                channelMenuExpanded = false
                            })
                        }
                        if (activeTrackIndex != null) {
                            DropdownMenuItem(text = { Text("✎ Перейменувати") }, onClick = {
                                renameText = s.tracks.getOrNull(activeTrackIndex!!)?.name ?: ""
                                renameDialogOpen = true
                                channelMenuExpanded = false
                            })
                        }
                    }
                }

                if (activeTrackIndex != null && !isDrumChannel) {
                    Box {
                        val curProgram = s.tracks.getOrNull(activeTrackIndex!!)?.program ?: 0
                        IconBtn(shortenName(GmInstruments.NAMES.getOrElse(curProgram) { "?" }, 12), wide = true) {
                            instrumentMenuExpanded = true
                        }
                        DropdownMenu(
                            expanded = instrumentMenuExpanded,
                            onDismissRequest = { instrumentMenuExpanded = false }
                        ) {
                            GmInstruments.NAMES.forEachIndexed { i, name ->
                                if (i % 8 == 0) {
                                    DropdownMenuItem(
                                        text = { Text("— ${GmInstruments.CATEGORIES[i / 8]} —", color = Color.Gray) },
                                        onClick = {},
                                        enabled = false
                                    )
                                }
                                DropdownMenuItem(text = { Text("${i + 1}. $name") }, onClick = {
                                    val idx = activeTrackIndex
                                    if (idx != null) {
                                        val newTracks = s.tracks.mapIndexed { ti, t ->
                                            if (ti == idx) t.copy(program = i) else t
                                        }
                                        updateSong(s.copy(tracks = newTracks))
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
                        DropdownMenuItem(text = { Text("Зберегти JSON") }, onClick = {
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
                        DropdownMenuItem(text = { Text("🎵 Експортувати .mid") }, onClick = {
                            menuExpanded = false
                            val current = song
                            if (current != null) {
                                val dir = context.getExternalFilesDir(null)
                                val safeTitle = current.title.ifBlank { "song" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                val file = File(dir, "${safeTitle}_${System.currentTimeMillis()}.mid")
                                realPlayer.exportToFile(current, file)
                                Toast.makeText(context, "Експортовано: ${file.name}", Toast.LENGTH_LONG).show()
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
                isDrumChannel = isDrumChannel,
                drumTrackIndices = drumTrackIndices,
                onSongChanged = { updated -> updateSong(updated) },
                onActiveTrackChangeForNewNotes = { idx -> activeTrackIndex = idx },
                onPreviewNote = { note, vel, durMs -> previewPlayer.previewNote(note, vel, durMs) },
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
                                updateSong(cur.copy(tracks = newTracks))
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
        onDispose {
            previewPlayer.release()
            realPlayer.stop()
        }
    }
}

private fun Double.roundToIntSafe(): Int = Math.round(this).toInt()

@Composable
private fun IconBtn(label: String, wide: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = if (wide) 10.dp else 6.dp, vertical = 6.dp),
        modifier = Modifier.padding(end = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label)
    }
}

@Composable
private fun TinyIconBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.padding(end = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label)
    }
}
