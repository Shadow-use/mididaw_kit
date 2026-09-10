package com.example.mididaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mididaw.model.MidiSong
import kotlin.math.max

private val trackColors = listOf(
    Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784),
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF64B5F6)
)

/**
 * Піано-рол з авто-масштабом по вертикалі: показує лише той діапазон нот,
 * який реально використовується в пісні (+невеликий запас), а не всі 0..127.
 * Додано вертикальний скрол про запас, якщо діапазон все одно великий.
 */
@Composable
fun PianoRollView(
    song: MidiSong,
    pxPerTick: Float = 0.05f,
    rowHeight: Float = 20f,
    modifier: Modifier = Modifier
) {
    val allEvents = song.tracks.flatMap { it.events }

    val maxTick = allEvents.maxOfOrNull { it.start + it.dur } ?: 0L

    val notesUsed = allEvents.map { it.note }
    val minNote = (notesUsed.minOrNull() ?: 48) - 3
    val maxNote = (notesUsed.maxOrNull() ?: 84) + 3
    val noteRange = max(1, maxNote - minNote)

    val widthDp = max(400f, maxTick * pxPerTick / 2f)   // приблизна конвертація px->dp
    val heightDp = noteRange * rowHeight / 2f            // те саме для висоти

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .width(widthDp.dp)
            .height(heightDp.dp)
    ) {
        song.tracks.forEachIndexed { trackIndex, track ->
            val color = trackColors[trackIndex % trackColors.size]
            for (e in track.events) {
                val x = e.start * pxPerTick
                val w = max(2f, e.dur * pxPerTick)
                // вище нота — вище на екрані; діапазон обрізаний під minNote..maxNote
                val y = (maxNote - e.note) * rowHeight

                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(w, rowHeight - 1f)
                )
            }
        }
    }
}
