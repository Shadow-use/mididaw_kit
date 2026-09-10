package com.example.mididaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    pxPerTick: Float = 0.05f,
    rowHeight: Float = 24f,
    onSongChanged: (MidiSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapTicks = max(1, initialSong.ticksPerBeat / 4) // сітка = 16-та нота

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

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    fun commit() {
        onSongChanged(rebuildSong(initialSong, notes))
    }

    val maxTick = (notes.maxOfOrNull { it.start + it.dur } ?: 0L)
    val minNote = (notes.minOfOrNull { it.note } ?: 48) - 3
    val maxNote = (notes.maxOfOrNull { it.note } ?: 84) + 3
    val noteRange = max(1, maxNote - minNote)

    val widthDp = max(400f, maxTick * pxPerTick / 2f)
    val heightDp = noteRange * rowHeight / 2f

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sel = selectedIndex
            Text(
                if (sel != null) "Виділено: нота ${notes[sel].note}" else "Тап по ноті — виділити"
            )
            if (sel != null) {
                Button(onClick = {
                    notes.removeAt(sel)
                    selectedIndex = null
                    commit()
                }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Видалити")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
            ) {
                notes.forEachIndexed { i, n ->
                    val color = trackColors[n.trackIndex % trackColors.size]
                    val xDp = n.start * pxPerTick
                    val wDp = max(4f, n.dur * pxPerTick)
                    val yDp = (maxNote - n.note) * rowHeight
                    val isSelected = selectedIndex == i

                    // тіло ноти: тап = вибрати, drag = зсув по часу і висоті
                    Box(
                        modifier = Modifier
                            .offset(x = xDp.dp, y = yDp.dp)
                            .size(wDp.dp, (rowHeight - 1f).dp)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.Black) else Modifier
                            )
                            .pointerInput(n.id) {
                                detectTapGestures(onTap = { selectedIndex = i })
                            }
                            .pointerInput(n.id) {
                                detectDragGestures(
                                    onDragEnd = { commit() }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val dxDp = dragAmount.x / density.density
                                    val dyDp = dragAmount.y / density.density

                                    val cur = notes[i]
                                    val deltaTicks = (dxDp / pxPerTick).roundToInt()
                                    val newStartRaw = max(0L, cur.start + deltaTicks)
                                    val newStart = (newStartRaw / snapTicks) * snapTicks.toLong()

                                    val deltaSemis = (dyDp / rowHeight).roundToInt()
                                    val newNote = (cur.note - deltaSemis).coerceIn(0, 127)

                                    notes[i] = cur.copy(start = newStart, note = newNote)
                                }
                            }
                    )

                    // хендл для зміни тривалості — смужка з правого краю
                    val handleWidthDp = 10f
                    Box(
                        modifier = Modifier
                            .offset(x = (xDp + wDp - handleWidthDp).dp, y = yDp.dp)
                            .size(handleWidthDp.dp, (rowHeight - 1f).dp)
                            .background(Color.Black.copy(alpha = 0.25f))
                            .pointerInput(n.id) {
                                detectDragGestures(
                                    onDragEnd = { commit() }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val dxDp = dragAmount.x / density.density
                                    val cur = notes[i]
                                    val deltaTicks = (dxDp / pxPerTick).roundToInt()
                                    val newDurRaw = max(snapTicks.toLong(), cur.dur + deltaTicks)
                                    val newDur = (newDurRaw / snapTicks) * snapTicks.toLong()
                                    notes[i] = cur.copy(dur = newDur)
                                }
                            }
                    )
                }
            }
        }
    }
}
