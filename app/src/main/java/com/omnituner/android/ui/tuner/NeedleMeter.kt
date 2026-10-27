package com.omnituner.android.ui.tuner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnituner.android.data.IN_TUNE_TOLERANCE_CENTS
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RANGE_CENTS = 50
private const val TICK_STEP_CENTS = 5
private val MAJOR_TICKS = setOf(0, 10, 25, 50)
private val LABELLED_TICKS = listOf(-50, -25, 0, 25, 50)

// The ruler and the needle share one span so +/-50 lands exactly on the end tick.
private const val SPAN_START = 0.02f
private const val SPAN_WIDTH = 0.96f

@Composable
fun NeedleMeter(
    needlePercent: Float,
    needleColor: Color,
    cents: Double?,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    scaleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = scaleColor)
    val readout = cents?.coerceIn(-50.0, 50.0)?.roundToInt() ?: 0
    val offScale = cents != null && abs(cents) > RANGE_CENTS
    // No pitch: the needle fades out, leaving only a faint mark at 0 instead of a false "perfect".
    val needleAlpha by animateFloatAsState(
        targetValue = when {
            cents == null -> 0f
            offScale || dimmed -> 0.5f
            else -> 1f
        },
        animationSpec = tween(200),
        label = "needleAlpha",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics {
                contentDescription = if (cents == null) {
                    "Tuning meter, no pitch detected"
                } else {
                    "Tuning meter, needle at $readout cents, range -50 to +50"
                }
            },
    ) {
        val width = size.width
        val centerY = size.height * 0.42f
        val majorHeight = size.height * 0.30f
        val minorHeight = size.height * 0.18f
        fun xAt(cents: Float) = width * (SPAN_START + SPAN_WIDTH * (cents + RANGE_CENTS) / (2 * RANGE_CENTS))

        // In-tune zone: a soft band behind the centre ticks.
        val bandLeft = xAt(-IN_TUNE_TOLERANCE_CENTS.toFloat())
        val bandRight = xAt(IN_TUNE_TOLERANCE_CENTS.toFloat())
        val bandHeight = majorHeight * 1.7f
        drawRoundRect(
            color = needleColor.copy(alpha = 0.14f),
            topLeft = Offset(bandLeft, centerY - bandHeight / 2),
            size = Size(bandRight - bandLeft, bandHeight),
            cornerRadius = CornerRadius((bandRight - bandLeft) / 2),
        )

        for (c in -RANGE_CENTS..RANGE_CENTS step TICK_STEP_CENTS) {
            val major = abs(c) in MAJOR_TICKS
            val tickHeight = if (major) majorHeight else minorHeight
            val x = xAt(c.toFloat())
            drawLine(
                color = scaleColor.copy(alpha = if (major) 0.75f else 0.45f),
                start = Offset(x, centerY - tickHeight / 2),
                end = Offset(x, centerY + tickHeight / 2),
                strokeWidth = if (major) 3f else 2f,
                cap = StrokeCap.Round,
            )
        }

        val needleX = width * (SPAN_START + SPAN_WIDTH * (needlePercent / 100f).coerceIn(0f, 1f))
        val needleHalf = majorHeight * 1.1f
        // Resting mark at 0, visible only while the needle is away.
        drawLine(
            color = needleColor.copy(alpha = 0.35f * (1f - needleAlpha)),
            start = Offset(xAt(0f), centerY - majorHeight * 0.5f),
            end = Offset(xAt(0f), centerY + majorHeight * 0.5f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = needleColor.copy(alpha = needleAlpha),
            start = Offset(needleX, centerY - needleHalf),
            end = Offset(needleX, centerY + needleHalf),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )

        // Labels sit clear of the needle's lower end.
        val labelTop = centerY + needleHalf + 8.dp.toPx()
        LABELLED_TICKS.forEach { c ->
            val label = when {
                c > 0 -> "+$c"
                else -> "$c"
            }
            val measured = textMeasurer.measure(
                text = label,
                style = labelStyle.copy(
                    color = if (c == 0) needleColor else scaleColor,
                    fontWeight = if (c == 0) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            drawText(measured, topLeft = Offset(xAt(c.toFloat()) - measured.size.width / 2f, labelTop))
        }
    }
}
