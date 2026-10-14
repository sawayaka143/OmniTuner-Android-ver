package com.omnituner.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.omnituner.core.theory.FretCell

private val SINGLE_MARKER_FRETS = setOf(3, 5, 7, 9, 15, 17, 19, 21)
private val DOUBLE_MARKER_FRETS = setOf(12, 24)

@Composable
fun FretboardCanvas(
    board: List<List<FretCell>>,
    showLabels: Boolean,
    useNoteNames: Boolean,
    showOutside: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val wireColor = scheme.onSurface
    val inkColor = scheme.onSurfaceVariant
    val fretCount = board.firstOrNull()?.size?.minus(1) ?: 12
    val columns = fretCount + 1

    Canvas(modifier = modifier) {
        if (board.isEmpty()) return@Canvas
        val strings = board.size
        val colWidth = size.width / columns
        val labelStyle = TextStyle(fontSize = 10.sp, color = inkColor)
        val pad = 14.sp.toPx()
        val boardTop = pad
        val boardBottom = size.height - pad
        val rowHeight = (boardBottom - boardTop) / strings

        fun centerX(col: Int) = colWidth * (col + 0.5f)

        val openLabel = textMeasurer.measure("OPEN", labelStyle)
        drawText(openLabel, topLeft = Offset(centerX(0) - openLabel.size.width / 2f, 0f))

        drawLine(
            color = wireColor.copy(alpha = 0.7f),
            start = Offset(colWidth, boardTop),
            end = Offset(colWidth, boardBottom),
            strokeWidth = 4f,
        )
        for (f in 1..fretCount) {
            val x = colWidth * (f + 1)
            drawLine(
                color = wireColor.copy(alpha = 0.25f),
                start = Offset(x, boardTop),
                end = Offset(x, boardBottom),
                strokeWidth = 1.5f,
            )
        }
        for (s in 0 until strings) {
            val y = boardTop + rowHeight * (s + 0.5f)
            drawLine(
                color = wireColor.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (strings >= 6) 2f else 3f,
            )
        }

        val markerColor = inkColor.copy(alpha = 0.45f)
        val markerHalf = 2.5.sp.toPx()
        fun diamond(cx: Float, cy: Float) {
            drawPath(
                Path().apply {
                    moveTo(cx, cy - markerHalf)
                    lineTo(cx + markerHalf, cy)
                    lineTo(cx, cy + markerHalf)
                    lineTo(cx - markerHalf, cy)
                    close()
                },
                color = markerColor,
            )
        }
        for (f in 1..fretCount) {
            val cx = centerX(f)
            when {
                f in DOUBLE_MARKER_FRETS -> {
                    diamond(cx, boardTop + rowHeight * (strings / 2f - 1))
                    diamond(cx, boardTop + rowHeight * (strings / 2f + 1))
                }
                f in SINGLE_MARKER_FRETS -> diamond(cx, boardTop + rowHeight * (strings / 2f))
            }
        }

        val radius = minOf(rowHeight, colWidth) * 0.36f
        for (row in board) {
            for (cell in row) {
                val interval = cell.interval
                if (interval == null && !showOutside) continue
                val cx = centerX(cell.fret)
                val cy = boardTop + rowHeight * (cell.stringIndex + 0.5f)
                val cellColor = when {
                    cell.isRoot -> scheme.primary
                    interval != null -> scheme.secondaryContainer
                    else -> inkColor.copy(alpha = 0.35f)
                }
                drawCircle(
                    color = cellColor,
                    radius = if (cell.isRoot) radius * 1.12f else radius,
                    center = Offset(cx, cy),
                )
                if (showLabels) {
                    val text = when {
                        interval != null -> if (useNoteNames) cell.noteName else interval.label
                        useNoteNames -> cell.noteName
                        else -> null
                    }
                    if (text != null) {
                        val ink = when {
                            cell.isRoot -> scheme.onPrimary
                            interval != null -> scheme.onSecondaryContainer
                            else -> inkColor
                        }
                        val measured = textMeasurer.measure(text, TextStyle(fontSize = 10.sp, color = ink))
                        drawText(
                            measured,
                            topLeft = Offset(cx - measured.size.width / 2f, cy - measured.size.height / 2f),
                        )
                    }
                }
            }
        }

        for (c in 0..fretCount) {
            val measured = textMeasurer.measure("$c", labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    centerX(c) - measured.size.width / 2f,
                    boardBottom + (pad - measured.size.height) / 2f,
                ),
            )
        }
    }
}
