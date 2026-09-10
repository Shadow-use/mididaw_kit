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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.mididaw.model.MidiEvent
import com.example.mididaw.model.MidiSong
import kotlin.math.max
import kotlin.math.roundToInt

private val trackColors = listOf(
    Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784),
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF64B5F6)
)

private val blackKeySemitones = setOf(1, 3, 6, 8, 10)

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
    onSongChanged: (MidiSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val ticksPerBeat = max(1, initialSong.ticksPerBeat)
    var pxPerTick by remember { mutableStateOf(0.08f) }
    var rowHeight by remember { mutableStateOf(28f) }
    val snapTicks = max(1, ticksPerBeat / 4) // сітка = 16-та нота

    var nextId = 0
    val notes = remember {
        mutableStateListOf<EditableNote>().apply {
            initialSong.tracks.forEachIndexed { trackIdx, track ->
                track.events.forEach { e ->
                    add(EditableNote(nextId++, trackIdx, e.note, e.start, e.dur, e.vel))
                }
            }
        }
    }

    val selectedIds = remember { mutableStateListOf<Int>() }
    val density = LocalDensity.current

    fun commit() = onSongChanged(rebuildSong(initialSong, notes))
    fun indexOfId(id: Int) = notes.indexOfFirst { it.id == id }

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
    }

    fun setSelectedDuration(fractionOfWhole: Double) {
        val dur = (ticksPerBeat * 4 * fractionOfWhole).roundToInt().toLong().coerceAtLeast(1L)
        selectedIds.forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) notes[idx] = notes[idx].copy(dur = dur)
        }
        commit()
    }

    fun deleteSelected() {
        selectedIds.toList().forEach { id ->
            val idx = indexOfId(id)
            if (idx >= 0) notes.removeAt(idx)
        }
        selectedIds.clear()
        commit()
    }

    val maxTick = notes.maxOfOrNull { it.start + it.dur } ?: (ticksPerBeat.toLong() * 4)
    val minNote = (notes.minOfOrNull { it.note } ?: 48) - 3
    val maxNote = (notes.maxOfOrNull { it.note } ?: 84) + 3
    val noteRange = max(1, maxNote - minNote)

    val widthDp = max(400f, maxTick * pxPerTick + 60f)
    val heightDp = noteRange * rowHeight

    Column(
        modifier = modifier.background(Color(0xFF121212))
    ) {
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

        // Панель виділення
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
            ) {
                Text("${selectedIds.size} нот", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                SmallBtn("✕ Зняти") { selectedIds.clear() }
                SmallBtn("🗑 Видалити") { deleteSelected() }
                SmallBtn("←") { nudgeSelected(-snapTicks.toLong(), 0) }
                SmallBtn("→") { nudgeSelected(snapTicks.toLong(), 0) }
                SmallBtn("↑") { nudgeSelected(0, 1) }
                SmallBtn("↓") { nudgeSelected(0, -1) }
                SmallBtn("Окт+") { nudgeSelected(0, 12) }
                SmallBtn("Окт−") { nudgeSelected(0, -12) }
                Text("|", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                SmallBtn("1/16") { setSelectedDuration(0.0625) }
                SmallBtn("1/8") { setSelectedDuration(0.125) }
                SmallBtn("1/4") { setSelectedDuration(0.25) }
                SmallBtn("1/2") { setSelectedDuration(0.5) }
                SmallBtn("1/1") { setSelectedDuration(1.0) }
            }
        } else {
            Text(
                "Тап по ноті — виділити (декілька тапів — декілька нот)",
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color(0xFF121212))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
            ) {
                // Сітка
                Canvas(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                ) {
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

                    Box(
                        modifier = Modifier
                            .offset(x = xDp.dp, y = yDp.dp)
                            .size(wDp.dp, (rowHeight - 2f).dp)
                            .background(color)
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
                                    onDragEnd = { snapSelectedToGrid() },
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

                    // хендл для точної зміни тривалості перетягуванням
                    val handleWidthDp = 10f
                    Box(
                        modifier = Modifier
                            .offset(x = (xDp + wDp - handleWidthDp).dp, y = yDp.dp)
                            .size(handleWidthDp.dp, (rowHeight - 2f).dp)
                            .background(Color.White.copy(alpha = 0.3f))
                            .pointerInput(n.id) {
                                detectDragGestures(
                                    onDragEnd = { commit() }
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
