package com.omnituner.android.ui.chords

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.omnituner.android.data.DiagramLabels
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalStatusColors
import com.omnituner.core.theory.DEGREE_LABELS
import com.omnituner.core.theory.ParsedTuning
import com.omnituner.core.theory.mod12
import com.omnituner.core.theory.pcName

// The web NeckDiagram's geometry, in its SVG units; the canvas scales them to the width it gets.
private const val ROW = 24f
private const val COL = 30f
private const val LEFT = 22f
private const val IND = 26f
private const val PAD = 8f
// Gap between the open column and a window that starts above fret 1 while strings still ring open.
private const val BREAK = 10f
private const val TOP = 6f
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
    return ((widthUnits - LEFT - IND - PAD) / COL).toInt().coerceAtLeast(MIN_COLUMNS)
}

/**
 * One fingering on a horizontal neck, lowest string at the bottom: tuning labels on the left, an
 * open/mute column and the fretted window (at least four frets). The window starts at the nut
 * whenever every fretted note fits in it from there; a higher shape that also rings open strings
 * gets a break (a gap and a doubled edge) between them, so its first fret never reads as fret 1.
 * Each ring holds its note's name or degree; fret numbers mark only the inlay frets and, away from
 * the nut, the window's first fret. [minColumns] widens the window (to the right, never past the
 * last fret) to fill a wide box.
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
    val window = maxOf(minColumns, MIN_COLUMNS)
    // Open strings only read right against the nut, so start there whenever the shape fits.
    val lo = if ((fretted.maxOrNull() ?: 0) <= window) 1 else fretted.min()
    val reach = minOf(lo + window - 1, maxOf(LAST_FRET, lo + MIN_COLUMNS - 1))
    val hi = maxOf(fretted.maxOrNull() ?: lo, reach)
    val cols = hi - lo + 1
    val broken = lo > 1 && frets.any { it == 0 }
    val gridL = LEFT + IND + if (broken) BREAK else 0f
    val gridR = gridL + cols * COL
    val width = gridR + PAD
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

    Canvas(
        modifier = modifier
            // Fills the width, unless a bounded height runs out first; then it fits the height.
            .aspectRatio(width / height)
            .semantics { contentDescription = "$symbol fingering ${index + 1}" },
    ) {
        val unit = size.width / width
        fun u(value: Float): Float = value * unit
        fun label(text: AnnotatedString, x: Float, y: Float, sizeUnits: Float, color: Color, weight: FontWeight, anchor: Anchor) =
            drawLabel(measurer, text, Offset(u(x), u(y)), u(sizeUnits), color, weight, anchor)
        fun label(text: String, x: Float, y: Float, sizeUnits: Float, color: Color, weight: FontWeight = FontWeight.Normal, anchor: Anchor = Anchor.Center) =
            label(AnnotatedString(text), x, y, sizeUnits, color, weight, anchor)

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
        if (broken) {
            val x = u(gridL - 3)
            drawLine(colors.medium, Offset(x, u(yTop - 2)), Offset(x, u(yBot + 2)), strokeWidth = u(1f))
        }

        for (s in 0 until n) {
            val y = u(stringY(s))
            if (broken) {
                drawLine(colors.medium, Offset(u(LEFT), y), Offset(u(LEFT + IND), y), strokeWidth = u(1f))
                drawLine(colors.medium, Offset(u(gridL), y), Offset(u(gridR), y), strokeWidth = u(1f))
            } else {
                drawLine(colors.medium, Offset(u(LEFT), y), Offset(u(gridR), y), strokeWidth = u(1f))
            }
            val muted = frets.getOrNull(s) == null
            label(pcName(mod12(tuning.midi[s]), tuning.flats), LEFT - 4, stringY(s), 8f, if (muted) colors.dim else colors.label, anchor = Anchor.End)
        }

        val axisY = yBot + BOT / 2 + 1
        if (frets.any { it == 0 }) label("0", indCx, axisY, 8f, colors.dim)
        for (f in lo until lo + cols) {
            if (f in INLAY_SINGLE || f in INLAY_DOUBLE || (f == lo && lo > 1)) label("$f", fretX(f), axisY, 8f, colors.dim)
        }

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
            val name = when (labels) {
                DiagramLabels.Notes -> pcName(mod12(midi), flats)
                DiagramLabels.Degrees -> DEGREE_LABELS[degree]
            }
            label(name, x, y, 9f, colors.ringText, FontWeight.Bold)
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
