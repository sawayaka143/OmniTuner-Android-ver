package com.omnituner.android.ui.chords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.omnituner.android.data.DiagramLabels
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalStatusColors
import com.omnituner.core.theory.DEGREE_LABELS
import com.omnituner.core.theory.ParsedTuning
import com.omnituner.core.theory.midiName
import com.omnituner.core.theory.mod12

// The web NeckDiagram's geometry, in its SVG units; the canvas scales them to the width it gets.
private const val ROW = 24f
private const val COL = 30f
private const val LEFT = 40f
private const val IND = 26f
private const val RIGHT = 34f
private const val PAD = 8f
private const val TOP = 24f
private const val BOT = 18f
private const val RING = 9f
private const val MIN_COLUMNS = 4
private const val LAST_FRET = 24

private val INLAY_SINGLE = setOf(3, 5, 7, 9, 15, 17, 19, 21)
private val INLAY_DOUBLE = setOf(12, 24)

private class NeckColors(
    val root: Color,
    val third: Color,
    val fifth: Color,
    val alt: Color,
    val other: Color,
    val subtle: Color,
    val medium: Color,
    val nut: Color,
    val label: Color,
    val dim: Color,
    val ring: Color,
    val ringText: Color,
) {
    // Ring color by the note's role in the chord, as semitones above the root.
    fun forDegree(degree: Int): Color = when (degree) {
        0 -> root
        3, 4 -> third
        5, 7 -> fifth
        6, 8, 10, 11 -> alt
        else -> other
    }
}

private enum class Anchor { Start, Center, End }

/**
 * How many frets a diagram for [strings] strings can show and still fit a box of width/height
 * [aspect] at full height: passed as `minColumns`, the window grows to the right until the diagram
 * is about as wide as the box, instead of leaving it empty beside a four-fret diagram.
 */
fun neckDiagramColumnsFor(aspect: Float, strings: Int): Int {
    val widthUnits = aspect * (TOP + strings * ROW + BOT)
    return ((widthUnits - LEFT - IND - RIGHT - PAD) / COL).toInt().coerceAtLeast(MIN_COLUMNS)
}

/**
 * One fingering on a horizontal neck, lowest string at the bottom: tuning labels on the left, an
 * open/mute column, the fretted window (at least four frets, a nut when it starts at fret 1),
 * fret numbers underneath and each note's name or degree at the right edge. [minColumns] widens
 * the window (to the right, never past the last fret) to fill a wide box.
 */
@Composable
fun NeckDiagram(
    frets: List<Int?>,
    tuning: ParsedTuning,
    symbol: String,
    rootPc: Int,
    index: Int,
    labels: DiagramLabels,
    flats: Boolean,
    modifier: Modifier = Modifier,
    minColumns: Int = MIN_COLUMNS,
) {
    val n = tuning.midi.size
    val fretted = frets.filterNotNull().filter { it > 0 }
    val lo = maxOf(1, fretted.minOrNull() ?: 1)
    val reach = minOf(lo + maxOf(minColumns, MIN_COLUMNS) - 1, maxOf(LAST_FRET, lo + MIN_COLUMNS - 1))
    val hi = maxOf(fretted.maxOrNull() ?: lo, reach)
    val cols = hi - lo + 1
    val gridL = LEFT + IND
    val gridR = gridL + cols * COL
    val width = gridR + RIGHT + PAD
    val height = TOP + n * ROW + BOT
    val yTop = TOP
    val yBot = TOP + n * ROW
    val indCx = LEFT + IND / 2
    fun stringY(s: Int): Float = yTop + (n - 1 - s) * ROW + ROW / 2
    fun fretX(f: Int): Float = gridL + (f - lo) * COL + COL / 2

    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val colors = NeckColors(
        root = LocalAccentColors.current.accent,
        third = scheme.tertiary,
        fifth = scheme.onSurfaceVariant,
        alt = status.warn,
        other = status.info,
        subtle = scheme.outlineVariant,
        medium = scheme.outline,
        nut = scheme.onSurfaceVariant,
        label = scheme.onSurfaceVariant,
        dim = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        ring = scheme.surfaceContainer,
        ringText = scheme.onSurface,
    )
    val measurer = rememberTextMeasurer()
    val shape = MaterialTheme.shapes.small

    Canvas(
        modifier = modifier
            // Fills the width, unless a bounded height runs out first; then it fits the height.
            .aspectRatio(width / height)
            .background(scheme.surfaceContainerLow, shape)
            .border(1.dp, colors.subtle, shape)
            .semantics { contentDescription = "$symbol fingering ${index + 1}" },
    ) {
        val unit = size.width / width
        fun u(value: Float): Float = value * unit
        fun label(text: AnnotatedString, x: Float, y: Float, sizeUnits: Float, color: Color, weight: FontWeight, anchor: Anchor) =
            drawLabel(measurer, text, Offset(u(x), u(y)), u(sizeUnits), color, weight, anchor)
        fun label(text: String, x: Float, y: Float, sizeUnits: Float, color: Color, weight: FontWeight = FontWeight.Normal, anchor: Anchor = Anchor.Center) =
            label(AnnotatedString(text), x, y, sizeUnits, color, weight, anchor)

        val header = buildAnnotatedString {
            append(symbol)
            withStyle(SpanStyle(color = colors.dim, fontWeight = FontWeight.Normal, fontSize = (u(8f)).toSp())) {
                append("  #${index + 1}")
            }
        }
        label(header, 10f, 11f, 11f, colors.root, FontWeight.Bold, Anchor.Start)

        for (f in lo until lo + cols) {
            val cx = u(fretX(f))
            if (f in INLAY_SINGLE) {
                drawCircle(colors.subtle, u(4.5f), Offset(cx, u((yTop + yBot) / 2)))
            } else if (f in INLAY_DOUBLE) {
                drawCircle(colors.subtle, u(4.5f), Offset(cx, u(yTop + 0.3f * (yBot - yTop))))
                drawCircle(colors.subtle, u(4.5f), Offset(cx, u(yTop + 0.7f * (yBot - yTop))))
            }
        }

        for (k in 0..cols) {
            val x = u(gridL + k * COL)
            val nut = k == 0 && lo == 1
            val color = when {
                nut -> colors.nut
                k == 0 -> colors.medium
                else -> colors.subtle
            }
            drawLine(color, Offset(x, u(yTop - 2)), Offset(x, u(yBot + 2)), strokeWidth = u(if (nut) 4f else 1f))
        }

        for (s in 0 until n) {
            val y = u(stringY(s))
            drawLine(colors.medium, Offset(u(gridL - 26), y), Offset(u(gridR), y), strokeWidth = u(1f))
            val muted = frets.getOrNull(s) == null
            label(tuning.labels[s], gridL - 30, stringY(s), 8f, if (muted) colors.dim else colors.label, anchor = Anchor.End)
        }

        val axisY = yBot + BOT / 2 + 1
        if (frets.any { it == 0 }) label("0", indCx, axisY, 8f, colors.dim)
        for (k in 0..cols) label("${lo + k}", gridL + k * COL + COL / 2, axisY, 8f, colors.dim)

        for (s in 0 until n) {
            val fret = frets.getOrNull(s)
            val y = stringY(s)
            if (fret == null) {
                label("x", indCx, y, 10f, colors.alt, FontWeight.Bold)
                continue
            }
            val midi = tuning.midi[s] + fret
            val degree = mod12(midi - rootPc)
            val color = colors.forDegree(degree)
            val x = if (fret == 0) indCx else fretX(fret)
            val center = Offset(u(x), u(y))
            drawCircle(colors.ring, u(RING), center)
            drawCircle(color, u(RING), center, style = Stroke(width = u(2f)))
            label("$fret", x, y, 9f, colors.ringText, FontWeight.Bold)
            val edge = when (labels) {
                DiagramLabels.Notes -> midiName(midi, flats)
                DiagramLabels.Degrees -> DEGREE_LABELS[degree]
            }
            label(edge, gridR + 30, y, 9f, color, anchor = Anchor.End)
        }
    }
}

// Draws [text] vertically centred on [at], horizontally anchored like SVG's text-anchor.
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: AnnotatedString,
    at: Offset,
    sizePx: Float,
    color: Color,
    weight: FontWeight,
    anchor: Anchor,
) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = weight))
    val x = when (anchor) {
        Anchor.Start -> at.x
        Anchor.Center -> at.x - layout.size.width / 2f
        Anchor.End -> at.x - layout.size.width
    }
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}
