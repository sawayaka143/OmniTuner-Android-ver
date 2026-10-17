package com.omnituner.android.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.core.theory.FretCell

private val SINGLE_MARKER_FRETS = setOf(3, 5, 7, 9, 15, 17, 19, 21)
private val DOUBLE_MARKER_FRETS = setOf(12, 24)

private val OPEN_WIDTH = 40.dp
private val TOP_PAD = 10.dp
private val AXIS_HEIGHT = 20.dp
private val NUT_WIDTH = 4.dp
// Frets shrink to fit the width down to MIN_FIT_COLUMN; past that (long necks on a phone) they
// keep SCROLL_COLUMN and the frets scroll sideways under the pinned open-string column.
private val MIN_FIT_COLUMN = 26.dp
private val SCROLL_COLUMN = 34.dp
private val MAX_DOT_RADIUS = 14.dp
// Rows when the board is fitted to a given height (fitHeight), so it neither cramps nor balloons.
private val MIN_FIT_ROW = 22.dp
private val MAX_FIT_ROW = 48.dp

private fun rowHeightFor(strings: Int): Dp = when {
    strings <= 6 -> 36.dp
    strings <= 8 -> 32.dp
    else -> 28.dp
}

private class BoardColors(
    val panel: Color,
    val string: Color,
    val fret: Color,
    val nut: Color,
    val inlay: Color,
    val axis: Color,
    val tone: Color,
    val outside: Color,
    val accent: Color,
    val onAccent: Color,
)

/**
 * The note sounding right now and, when it has one, the single board position it is played at.
 * A plain class on purpose: every new note is a new instance, so the pop replays even on the
 * same spot.
 */
class SoundingNote(val midi: Int, val stringIndex: Int?, val fret: Int?)

/**
 * A scale map on a horizontal neck in the Chords diagram's style, highest string on top: the
 * open strings in a pinned column left of the nut, frets to the right (scrolling sideways when
 * they don't fit), fret numbers underneath. Scale tones are rings with the note or degree inside,
 * the root is filled with the accent, and [sounding] lights the one position being played.
 * With [fitHeight] the rows share the height the board is given instead of their fixed size.
 */
@Composable
fun FretboardCanvas(
    board: List<List<FretCell>>,
    useNoteNames: Boolean,
    showOutside: Boolean,
    sounding: SoundingNote?,
    onCellTap: (FretCell) -> Unit,
    modifier: Modifier = Modifier,
    fitHeight: Boolean = false,
) {
    if (board.isEmpty()) return
    val strings = board.size
    val fretCount = board.first().size - 1

    val scheme = MaterialTheme.colorScheme
    val accentColors = LocalAccentColors.current
    val colors = BoardColors(
        panel = scheme.surfaceContainerLow,
        string = scheme.outline,
        fret = scheme.outlineVariant,
        nut = scheme.onSurfaceVariant,
        inlay = scheme.outlineVariant,
        axis = scheme.onSurfaceVariant,
        tone = scheme.onSurface,
        outside = scheme.onSurfaceVariant,
        accent = accentColors.accent,
        onAccent = accentColors.onAccent,
    )
    val measurer = rememberTextMeasurer()
    val shape = MaterialTheme.shapes.small

    // A short pop each time a new note starts sounding.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(sounding) {
        if (sounding != null) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(durationMillis = 300))
        }
    }

    fun tappable(cell: FretCell?) = cell != null && (cell.interval != null || showOutside)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.fret, shape),
    ) {
        val rowHeight = if (fitHeight) {
            ((maxHeight - TOP_PAD - AXIS_HEIGHT) / strings).coerceIn(MIN_FIT_ROW, MAX_FIT_ROW)
        } else {
            rowHeightFor(strings)
        }
        val height = TOP_PAD + rowHeight * strings + AXIS_HEIGHT
        val fitColumn = (maxWidth - OPEN_WIDTH) / fretCount
        val column = if (fitColumn >= MIN_FIT_COLUMN) fitColumn else SCROLL_COLUMN
        val radius = minOf(column * 0.42f, rowHeight * 0.4f, MAX_DOT_RADIUS)

        Row(modifier = Modifier.height(height)) {
            Canvas(
                modifier = Modifier
                    .width(OPEN_WIDTH)
                    .fillMaxHeight()
                    .pointerInput(board, showOutside) {
                        detectTapGestures { offset ->
                            val row = ((offset.y - TOP_PAD.toPx()) / rowHeight.toPx()).toInt()
                            val cell = board.getOrNull(row)?.firstOrNull()
                            if (offset.y >= TOP_PAD.toPx() && tappable(cell)) onCellTap(cell!!)
                        }
                    },
            ) {
                val rowPx = rowHeight.toPx()
                val top = TOP_PAD.toPx()
                drawStrings(strings, top, rowPx, colors.string)
                val nutX = size.width - NUT_WIDTH.toPx() / 2
                drawLine(colors.nut, Offset(nutX, top), Offset(nutX, top + rowPx * strings), strokeWidth = NUT_WIDTH.toPx())
                val cx = (size.width - NUT_WIDTH.toPx()) / 2
                for ((s, row) in board.withIndex()) {
                    val cell = row.first()
                    val cy = top + rowPx * (s + 0.5f)
                    if (cell.interval == null && !showOutside) {
                        // Not in the scale: still name the open string, on a gap in the string.
                        drawCircle(colors.panel, radius.toPx() * 0.8f, Offset(cx, cy))
                        drawCenteredText(measurer, cell.noteName, Offset(cx, cy), colors.axis, FontWeight.Normal)
                    } else {
                        drawNote(measurer, cell, Offset(cx, cy), radius.toPx(), useNoteNames, sounding, pulse.value, colors)
                    }
                }
                drawCenteredText(measurer, "0", Offset(cx, top + rowPx * strings + AXIS_HEIGHT.toPx() / 2), colors.axis, FontWeight.Normal)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Canvas(
                    modifier = Modifier
                        .width(column * fretCount)
                        .fillMaxHeight()
                        .pointerInput(board, showOutside, column) {
                            detectTapGestures { offset ->
                                val fret = (offset.x / column.toPx()).toInt() + 1
                                val row = ((offset.y - TOP_PAD.toPx()) / rowHeight.toPx()).toInt()
                                val cell = board.getOrNull(row)?.getOrNull(fret)
                                if (offset.y >= TOP_PAD.toPx() && tappable(cell)) onCellTap(cell!!)
                            }
                        },
                ) {
                    val rowPx = rowHeight.toPx()
                    val colPx = column.toPx()
                    val top = TOP_PAD.toPx()
                    val bottom = top + rowPx * strings
                    fun centerX(fret: Int) = colPx * (fret - 0.5f)

                    val inlayRadius = 4.dp.toPx()
                    for (f in 1..fretCount) {
                        when (f) {
                            in DOUBLE_MARKER_FRETS -> {
                                drawCircle(colors.inlay, inlayRadius, Offset(centerX(f), top + 0.3f * (bottom - top)))
                                drawCircle(colors.inlay, inlayRadius, Offset(centerX(f), top + 0.7f * (bottom - top)))
                            }
                            in SINGLE_MARKER_FRETS -> drawCircle(colors.inlay, inlayRadius, Offset(centerX(f), (top + bottom) / 2))
                        }
                        val x = colPx * f
                        drawLine(colors.fret, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                    }
                    drawStrings(strings, top, rowPx, colors.string)

                    for ((s, row) in board.withIndex()) {
                        val cy = top + rowPx * (s + 0.5f)
                        for (cell in row.drop(1)) {
                            if (cell.interval == null && !showOutside) continue
                            drawNote(measurer, cell, Offset(centerX(cell.fret), cy), radius.toPx(), useNoteNames, sounding, pulse.value, colors)
                        }
                    }

                    val axisY = bottom + AXIS_HEIGHT.toPx() / 2
                    for (f in 1..fretCount) {
                        drawCenteredText(measurer, "$f", Offset(centerX(f), axisY), colors.axis, FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// Strings thicken toward the low (bottom) string, like the web board.
private fun DrawScope.drawStrings(strings: Int, top: Float, rowPx: Float, color: Color) {
    for (s in 0 until strings) {
        val y = top + rowPx * (s + 0.5f)
        val weight = 0.75f + 1.25f * s / maxOf(1, strings - 1)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = weight.dp.toPx())
    }
}

private fun DrawScope.drawNote(
    measurer: TextMeasurer,
    cell: FretCell,
    center: Offset,
    radius: Float,
    useNoteNames: Boolean,
    sounding: SoundingNote?,
    pulse: Float,
    colors: BoardColors,
) {
    val interval = cell.interval
    val isSounding = sounding != null && sounding.stringIndex == cell.stringIndex && sounding.fret == cell.fret
    val text = when {
        interval != null -> if (useNoteNames) cell.noteName else interval.label
        useNoteNames -> cell.noteName
        else -> null
    }
    when {
        isSounding -> {
            val r = radius * (1f + 0.15f * pulse)
            drawCircle(colors.accent.copy(alpha = 0.25f), r * 1.45f, center)
            drawCircle(colors.accent, r, center)
            text?.let { drawCenteredText(measurer, it, center, colors.onAccent, FontWeight.Bold) }
        }
        interval == null -> {
            // Outside the scale: a quiet name on a gap in the string, no ring.
            drawCircle(colors.panel, radius * 0.8f, center)
            text?.let { drawCenteredText(measurer, it, center, colors.outside, FontWeight.Normal) }
        }
        cell.isRoot -> {
            drawCircle(colors.accent, radius, center)
            text?.let { drawCenteredText(measurer, it, center, colors.onAccent, FontWeight.Bold) }
        }
        else -> {
            drawCircle(colors.panel, radius, center)
            drawCircle(colors.tone, radius - 0.75.dp.toPx(), center, style = Stroke(width = 1.5.dp.toPx()))
            text?.let { drawCenteredText(measurer, it, center, colors.tone, FontWeight.Bold) }
        }
    }
}

private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    weight: FontWeight,
) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = 11.sp, fontWeight = weight))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
