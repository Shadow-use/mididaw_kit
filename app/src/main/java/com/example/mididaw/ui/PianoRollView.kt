package com.example.mididaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
 * Проста візуалізація: ноти = прямокутники. Поки без редагування —
 * лише перегляд + горизонтальний скрол. Наступний крок — drag/resize жестами.
 */
@Composable
fun PianoRollView(
    song: MidiSong,
    pxPerTick: Float = 0.05f,
    rowHeight: Float = 14f,
    modifier: Modifier = Modifier
) {
    val maxTick = song.tracks
        .flatMap { it.events }
        .maxOfOrNull { it.start + it.dur } ?: 0L

    val widthDp = max(400f, maxTick * pxPerTick / 2f) // приблизна конвертація px->dp

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .width(widthDp.dp)
            .height(600.dp)
    ) {
        song.tracks.forEachIndexed { trackIndex, track ->
            val color = trackColors[trackIndex % trackColors.size]
            for (e in track.events) {
                val x = e.start * pxPerTick
                val w = max(2f, e.dur * pxPerTick)
                val y = (127 - e.note) * rowHeight // вища нота — вище на екрані

                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(w, rowHeight - 1f)
                )
            }
        }
    }
}
