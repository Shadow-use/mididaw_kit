package com.example.mididaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mididaw.midi.GmInstruments
import com.example.mididaw.model.MidiEvent
import com.example.mididaw.model.MidiSong
import kotlin.math.max
import kotlin.math.roundToInt

private val trackColors = listOf(
    Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784),
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF64B5F6)
)

private val blackKeySemitones = setOf(1, 3, 6, 8, 10)
private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private val durationSteps = listOf(0.0625, 0.125, 0.25, 0.5, 1.0)
private val durationLabels = listOf("1/16", "1/8", "1/4", "1/2", "1/1")

private fun noteLabel(n: Int): String {
    val octave = n / 12 - 1
    return "${noteNames[((n % 12) + 12) % 12]}$octave"
}

private data class EditableNote(
    val id: Int,
    val trackIndex: Int,
    val note: Int,
    val start: Long,
    val dur: Long,
    val vel: Int
)

private fun rebuildSong(original: MidiSong, notes: List<EditableNote>): MidiSong {
    val grouped = notes.groupBy { it.trackIndex }
    val newTracks = original.tracks.mapIndexed { idx, track ->
        val events = (grouped[idx] ?: emptyList())
            .sortedBy { it.start }
            .map { MidiEvent(it.note, it.start, it.dur, it.vel) }
        track.copy(events = events)
    }
    return original.copy(tracks = newTracks)
}

@Composable
fun EditablePianoRollView(
    initialSong: MidiSong,
    loadKey: Any,
    activeTrackIndex: Int?,
    currentPlaybackTick: Long?,
    isDrumChannel: Boolean = false,
    onSongChanged: (MidiSong) -> Unit,
    onActiveTrackChangeForNewNotes: (Int) -> Unit = {},
    onPreviewNote: (note: Int, durMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val ticksPerBeat = max(1, initialSong.ticksPerBeat)
    val msPerTick = 60000.0 / (initialSong.bpm * ticksPerBeat)
    var pxPerTick by remember { mutableStateOf(0.08f) }
    var rowHeight by remember { mutableStateOf(28f) }
    val snapTicks = max(1, ticksPerBeat / 4)

    val notes = remember(loadKey) {
        var nextId = 0
        mutableStateListOf<EditableNote>().apply {
            initialSong.tracks.forEachIndexed { trackIdx, track ->
                track.events.forEach { e ->
                    add(EditableNote(nextId++, trackIdx, e.note, e.start, e.dur, e.vel))
                }
            }
        }
    }
    var idCounter by remember(loadKey) { mutableStateOf(notes.size) }
    var durationIndex by remember(loadKey) { mutableStateOf(2) } // за замовч. 1/4

    val selectedIds = remember(loadKey) { mutableStateListOf<Int>() }
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    fun commit() = onSongChanged(rebuildSong(initialSong, notes))
    fun indexOfId(id: Int) = notes.indexOfFirst { it.id == id }
    fun previewDurMs(ticks: Long) = (ticks * msPerTick).roundToInt().toLong().coerceAtLeast(60L)

    fun moveSelectedRaw(dTicks: Long, dNote: Int) {
        selectedIds.forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) {
                val cur = notes[idx]
                val newStart = (cur.start + dTicks).coerceAtLeast(0)
                val newNote = (cur.note + dNote).coerceIn(0, 127)
                notes[idx] = cur.copy(start = newStart, note = newNote)
            }
        }
    }

    fun snapSelectedToGrid() {
        selectedIds.forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) {
                val cur = notes[idx]
                notes[idx] = cur.copy(start = (cur.start / snapTicks) * snapTicks)
            }
        }
        commit()
    }

    fun nudgeSelected(ticks: Long, semis: Int) {
        moveSelectedRaw(ticks, semis)
        commit()
        val firstId = selectedIds.firstOrNull()
        if (firstId != null) {
            val idx = indexOfId(firstId)
            if (idx >= 0) onPreviewNote(notes[idx].note, previewDurMs(notes[idx].dur))
        }
    }

    fun applyDuration(index: Int) {
        val dur = (ticksPerBeat * 4 * durationSteps[index]).roundToInt().toLong().coerceAtLeast(1L)
        selectedIds.forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) notes[idx] = notes[idx].copy(dur = dur)
        }
        commit()
        val firstId = selectedIds.firstOrNull()
        if (firstId != null) {
            val idx = indexOfId(firstId)
            if (idx >= 0) onPreviewNote(notes[idx].note, previewDurMs(notes[idx].dur))
        }
    }

    fun stepDuration(delta: Int) {
        durationIndex = (durationIndex + delta).coerceIn(0, durationSteps.lastIndex)
        applyDuration(durationIndex)
    }

    fun deleteSelected() {
        selectedIds.toList().forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) notes.removeAt(idx)
        }
        selectedIds.clear()
        commit()
    }

    val maxTick = max(
        notes.maxOfOrNull { it.start + it.dur } ?: (ticksPerBeat.toLong() * 4),
        (currentPlaybackTick ?: 0L) + ticksPerBeat
    )
    val minNote = (notes.minOfOrNull { it.note } ?: 48) - 3
    val maxNote = (notes.maxOfOrNull { it.note } ?: 84) + 3
    val noteRange = max(1, maxNote - minNote)

    val widthDp = max(400f, maxTick * pxPerTick + 60f)
    val heightDp = noteRange * rowHeight
    val rulerW = if (isDrumChannel) 92f else 52f

    Box(modifier = modifier.background(Color(0xFF121212))) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // Зум
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp)
            ) {
                Text("Час", color = Color.White, modifier = Modifier.padding(end = 4.dp))
                SmallBtn("−") { pxPerTick = (pxPerTick / 1.25f).coerceAtLeast(0.015f) }
                SmallBtn("+") { pxPerTick = (pxPerTick * 1.25f).coerceAtMost(0.6f) }
                Text("Висота", color = Color.White, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                SmallBtn("−") { rowHeight = (rowHeight / 1.15f).coerceAtLeast(12f) }
                SmallBtn("+") { rowHeight = (rowHeight * 1.15f).coerceAtMost(60f) }
            }

            // Верхня лінійка (номери тактів)
            Row {
                Box(modifier = Modifier.width(rulerW.dp).height(22.dp).background(Color(0xFF1A1A1A)))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .horizontalScroll(hScroll)
                        .background(Color(0xFF1A1A1A))
                ) {
                    Box(modifier = Modifier.width(widthDp.dp).height(22.dp)) {
                        val beatsTotal = (maxTick / ticksPerBeat).toInt() + 2
                        var b = 0
                        while (b <= beatsTotal) {
                            val x = b * ticksPerBeat * pxPerTick
                            Text(
                                text = "${b / 4 + 1}",
                                color = Color(0xFFCCCCCC),
                                fontSize = 10.sp,
                                modifier = Modifier.offset(x = x.dp, y = 4.dp)
                            )
                            b += 4
                        }
                    }
                }
            }

            // Лівий рядок нот + основне поле
            Row(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .width(rulerW.dp)
                        .verticalScroll(vScroll)
                        .background(Color(0xFF1A1A1A))
                ) {
                    Box(modifier = Modifier.height(heightDp.dp)) {
                        for (n in minNote..maxNote) {
                            val y = (maxNote - n) * rowHeight
                            val label = if (isDrumChannel) {
                                GmInstruments.DRUM_NAMES[n] ?: noteLabel(n)
                            } else {
                                noteLabel(n)
                            }
                            Text(
                                text = label,
                                color = if ((n % 12 + 12) % 12 in blackKeySemitones) Color(0xFF888888) else Color.White,
                                fontSize = 9.sp,
                                maxLines = 1,
                                modifier = Modifier.offset(x = 2.dp, y = y.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF121212))
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                ) {
                    Box(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .height(heightDp.dp)
                            .pointerInput(loadKey, activeTrackIndex, pxPerTick, rowHeight) {
                                detectTapGestures(onTap = { offset ->
                                    val xDp = offset.x / density.density
                                    val yDp = offset.y / density.density
                                    val tappedTick = (xDp / pxPerTick).toLong()
                                    val tappedNote = (maxNote - (yDp / rowHeight).toInt())

                                    val hitsExisting = notes.any { n ->
                                        val nx = n.start * pxPerTick
                                        val nw = max(6f, n.dur * pxPerTick)
                                        val ny = (maxNote - n.note) * rowHeight
                                        xDp in nx..(nx + nw) && yDp in ny..(ny + rowHeight)
                                    }
                                    if (!hitsExisting) {
                                        val snappedStart = (tappedTick / snapTicks) * snapTicks
                                        val trackIdx = activeTrackIndex ?: 0
                                        val newId = idCounter++
                                        val newNoteVal = tappedNote.coerceIn(0, 127)
                                        val newDur = (ticksPerBeat * 4 * durationSteps[durationIndex])
                                            .roundToInt().toLong().coerceAtLeast(1L)
                                        notes.add(
                                            EditableNote(
                                                id = newId,
                                                trackIndex = trackIdx,
                                                note = newNoteVal,
                                                start = snappedStart,
                                                dur = newDur,
                                                vel = 90
                                            )
                                        )
                                        selectedIds.clear()
                                        selectedIds.add(newId)
                                        onActiveTrackChangeForNewNotes(trackIdx)
                                        onPreviewNote(newNoteVal, previewDurMs(newDur))
                                        commit()
                                    }
                                })
                            }
                    ) {
                        // Сітка
                        Canvas(modifier = Modifier.width(widthDp.dp).height(heightDp.dp)) {
                            for (n in minNote..maxNote) {
                                val rowYDp = (maxNote - n) * rowHeight
                                if ((n % 12 + 12) % 12 in blackKeySemitones) {
                                    drawRect(
                                        color = Color(0xFF1A1A1A),
                                        topLeft = Offset(0f, rowYDp.dp.toPx()),
                                        size = Size(widthDp.dp.toPx(), rowHeight.dp.toPx())
                                    )
                                }
                                drawLine(
                                    color = Color(0xFF2A2A2A),
                                    start = Offset(0f, rowYDp.dp.toPx()),
                                    end = Offset(widthDp.dp.toPx(), rowYDp.dp.toPx()),
                                    strokeWidth = 1f
                                )
                            }
                            val beatsTotal = (maxTick / ticksPerBeat).toInt() + 2
                            for (b in 0..beatsTotal) {
                                val xDp = b * ticksPerBeat * pxPerTick
                                val isBar = b % 4 == 0
                                drawLine(
                                    color = if (isBar) Color(0xFF505050) else Color(0xFF2A2A2A),
                                    start = Offset(xDp.dp.toPx(), 0f),
                                    end = Offset(xDp.dp.toPx(), heightDp.dp.toPx()),
                                    strokeWidth = if (isBar) 2f else 1f
                                )
                            }
                        }

                        // Ноти
                        notes.forEachIndexed { i, n ->
                            val color = trackColors[n.trackIndex % trackColors.size]
                            val xDp = n.start * pxPerTick
                            val wDp = max(6f, n.dur * pxPerTick)
                            val yDp = (maxNote - n.note) * rowHeight
                            val isSelected = selectedIds.contains(n.id)
                            val isDimmed = activeTrackIndex != null && n.trackIndex != activeTrackIndex

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp.dp, y = yDp.dp)
                                    .size(wDp.dp, (rowHeight - 2f).dp)
                                    .background(if (isDimmed) color.copy(alpha = 0.25f) else color)
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Color.Yellow) else Modifier
                                    )
                                    .pointerInput(n.id) {
                                        detectTapGestures(onTap = {
                                            if (selectedIds.contains(n.id)) {
                                                selectedIds.remove(n.id)
                                            } else {
                                                selectedIds.add(n.id)
                                            }
                                            onPreviewNote(n.note, previewDurMs(n.dur))
                                        })
                                    }
                                    .pointerInput(n.id) {
                                        var accTicks = 0.0
                                        var accSemis = 0.0
                                        detectDragGestures(
                                            onDragStart = {
                                                accTicks = 0.0
                                                accSemis = 0.0
                                                if (!selectedIds.contains(n.id)) {
                                                    selectedIds.clear()
                                                    selectedIds.add(n.id)
                                                }
                                            },
                                            onDragEnd = {
                                                snapSelectedToGrid()
                                                val idx = indexOfId(n.id)
                                                if (idx >= 0) {
                                                    onPreviewNote(notes[idx].note, previewDurMs(notes[idx].dur))
                                                }
                                            },
                                            onDragCancel = { snapSelectedToGrid() }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            accTicks += (dragAmount.x / density.density) / pxPerTick
                                            accSemis += (dragAmount.y / density.density) / rowHeight
                                            val ticksDelta = accTicks.toInt()
                                            val semisDelta = accSemis.toInt()
                                            if (ticksDelta != 0 || semisDelta != 0) {
                                                moveSelectedRaw(ticksDelta.toLong(), -semisDelta)
                                                accTicks -= ticksDelta
                                                accSemis -= semisDelta
                                            }
                                        }
                                    }
                            )

                            val handleWidthDp = 10f
                            Box(
                                modifier = Modifier
                                    .offset(x = (xDp + wDp - handleWidthDp).dp, y = yDp.dp)
                                    .size(handleWidthDp.dp, (rowHeight - 2f).dp)
                                    .background(Color.White.copy(alpha = 0.3f))
                                    .pointerInput(n.id) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                commit()
                                                val idx = indexOfId(n.id)
                                                if (idx >= 0) {
                                                    onPreviewNote(notes[idx].note, previewDurMs(notes[idx].dur))
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val dxDp = dragAmount.x / density.density
                                            val idx = indexOfId(n.id)
                                            if (idx >= 0) {
                                                val cur = notes[idx]
                                                val deltaTicks = (dxDp / pxPerTick).roundToInt()
                                                val newDur = max(snapTicks.toLong(), cur.dur + deltaTicks)
                                                notes[idx] = cur.copy(dur = (newDur / snapTicks) * snapTicks)
                                            }
                                        }
                                    }
                            )
                        }

                        // Лінія відтворення
                        if (currentPlaybackTick != null) {
                            val playX = currentPlaybackTick * pxPerTick
                            Box(
                                modifier = Modifier
                                    .offset(x = playX.dp, y = 0.dp)
                                    .width(2.dp)
                                    .height(heightDp.dp)
                                    .background(Color(0xFFFF5252))
                            )
                        }
                    }
                }
            }
        }

        // Нижня напівпрозора панель команд — компактна, іконки без підписів
        if (selectedIds.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.82f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${selectedIds.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp, top = 8.dp)
                    )
                    TinyBtn("✕") { selectedIds.clear() }
                    TinyBtn("🗑") { deleteSelected() }
                    TinyBtn("←") { nudgeSelected(-snapTicks.toLong(), 0) }
                    TinyBtn("→") { nudgeSelected(snapTicks.toLong(), 0) }
                    TinyBtn("↑") { nudgeSelected(0, 1) }
                    TinyBtn("↓") { nudgeSelected(0, -1) }
                    TinyBtn("8+") { nudgeSelected(0, 12) }
                    TinyBtn("8−") { nudgeSelected(0, -12) }
                    TinyBtn("◀") { stepDuration(-1) }
                    Text(
                        durationLabels[durationIndex],
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp)
                    )
                    TinyBtn("▶") { stepDuration(1) }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = rulerW.dp + 6.dp, bottom = 4.dp)
            ) {
                Text("Тап по ноті — виділити", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                Text("Тап по пустому місцю — додати ноту", color = Color(0xFFAAAAAA), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.padding(end = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun TinyBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.padding(end = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
