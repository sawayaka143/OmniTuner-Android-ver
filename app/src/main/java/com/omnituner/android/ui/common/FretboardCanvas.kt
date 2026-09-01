package com.omnituner.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnituner.android.ui.theme.currentWebPalette
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.Tuning
import com.omnituner.core.theory.FretCell

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

/** Web textColorOn: ink that contrasts with an arbitrary interval cell color. */
private fun inkOn(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF121211) else Color(0xFFF5F5F3)

/**
 * Shared fretboard renderer for the scales explorer (interval cells) and the
 * chord finder (pitch-class highlight mode).
 */
@Composable
fun FretboardCanvas(
    board: List<List<FretCell>>,
    showLabels: Boolean,
    onCellTap: (FretCell) -> Unit,
    modifier: Modifier = Modifier,
    highlightPcs: Set<Int>? = null,
    dimNonHighlighted: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val palette = currentWebPalette()
    val wireColor = palette.text
    val stringLabelColor = palette.muted
    val highlightColor = palette.warn
    val fretCount = board.firstOrNull()?.size?.minus(1) ?: 12

    Canvas(modifier = modifier) {
        if (board.isEmpty()) return@Canvas
        val strings = board.size
        val labelColumnWidth = size.width * 0.09f
        val fretAreaWidth = size.width - labelColumnWidth
        val rowHeight = size.height / strings

        // nut + fret wires
        drawLine(
            color = wireColor.copy(alpha = 0.7f),
            start = Offset(labelColumnWidth, 0f),
            end = Offset(labelColumnWidth, size.height),
            strokeWidth = 4f,
        )
        for (fret in 1..fretCount) {
            val x = labelColumnWidth + fretAreaWidth * fret / fretCount
            drawLine(
                color = wireColor.copy(alpha = 0.25f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5f,
            )
        }
        // strings
        for (s in 0 until strings) {
            val y = rowHeight * (s + 0.5f)
            drawLine(
                color = wireColor.copy(alpha = 0.45f),
                start = Offset(labelColumnWidth, y),
                end = Offset(size.width, y),
                strokeWidth = if (strings >= 6) 2f else 3f,
            )
        }

        // cells
        for (row in board) {
            for (cell in row) {
                val highlight = highlightPcs?.contains(cell.pitchClass) == true
                if (cell.interval == null && highlightPcs == null) continue
                if (dimNonHighlighted && !highlight) continue

                val cx = labelColumnWidth + fretAreaWidth * (cell.fret + 0.5f) / fretCount
                val cy = rowHeight * (cell.stringIndex + 0.5f)
                val radius = minOf(rowHeight, fretAreaWidth / fretCount) * 0.36f

                if (highlightPcs != null) {
                    if (highlight) {
                        drawCircle(color = highlightColor, radius = radius, center = Offset(cx, cy))
                    }
                    continue
                }

                val interval = cell.interval
                if (interval != null) {
                    val cellColor = parseHexColor(cell.color, palette.dim)
                    drawCircle(
                        color = cellColor,
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                    if (showLabels) {
                        val text = interval.label
                        val measured = textMeasurer.measure(
                            text,
                            TextStyle(fontSize = 10.sp, color = inkOn(cellColor)),
                        )
                        drawText(
                            measured,
                            topLeft = Offset(
                                cx - measured.size.width / 2f,
                                cy - measured.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }

        // string labels
        for (row in board) {
            val first = row.firstOrNull() ?: continue
            val y = rowHeight * (first.stringIndex + 0.5f)
            val measured = textMeasurer.measure(
                first.noteName,
                TextStyle(fontSize = 10.sp, color = stringLabelColor),
            )
            drawText(
                measured,
                topLeft = Offset(
                    labelColumnWidth / 2 - measured.size.width / 2f,
                    y - measured.size.height / 2f,
                ),
            )
        }
    }

    // Tap handling overlay (fret positions mirror the canvas math)
    if (onCellTap != {}) {
        Box(
            modifier = modifier
                .clickable {
                    // precise per-cell handling would need pointerInput; tapped cell
                    // resolution lives in the scales screen's own overlay.
                },
        )
    }
}

private val NoOp: (FretCell) -> Unit = {}

@Composable
fun InstrumentTuningPicker(
    instruments: List<Instrument>,
    tunings: List<Tuning>,
    instrumentLabel: String,
    tuningLabel: String,
    onSelectInstrument: (String) -> Unit,
    onSelectTuning: (String) -> Unit,
) {
    var instrumentMenuOpen by remember { mutableStateOf(false) }
    var tuningMenuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            TextButton(onClick = { instrumentMenuOpen = true }) {
                Text("$instrumentLabel ▾")
            }
            DropdownMenu(
                expanded = instrumentMenuOpen,
                onDismissRequest = { instrumentMenuOpen = false },
            ) {
                for (instrument in instruments) {
                    DropdownMenuItem(
                        text = { Text(instrument.label) },
                        onClick = {
                            instrumentMenuOpen = false
                            onSelectInstrument(instrument.id)
                        },
                    )
                }
            }
        }
        Column {
            TextButton(onClick = { tuningMenuOpen = true }) {
                Text(tuningLabel)
            }
            DropdownMenu(
                expanded = tuningMenuOpen,
                onDismissRequest = { tuningMenuOpen = false },
            ) {
                for (tuning in tunings) {
                    DropdownMenuItem(
                        text = { Text(tuning.label) },
                        onClick = {
                            tuningMenuOpen = false
                            onSelectTuning(tuning.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
