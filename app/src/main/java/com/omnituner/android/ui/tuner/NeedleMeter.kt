package com.omnituner.android.ui.tuner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val TICK_COUNT = 41
private const val CENTER_TICK = 20

@Composable
fun NeedleMeter(
    needlePercent: Float,
    needleColor: Color,
    glow: Boolean,
    cents: Double?,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "glow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = baseColor)
    val readout = cents?.coerceIn(-50.0, 50.0)?.roundToInt() ?: 0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Tuning meter, needle at $readout cents, range -50 to +50" },
    ) {
        val width = size.width
        val centerY = size.height * 0.55f
        val majorHeight = size.height * 0.30f
        val minorHeight = size.height * 0.18f

        drawLine(
            color = baseColor.copy(alpha = 0.2f),
            start = Offset(width * 0.02f, centerY),
            end = Offset(width * 0.98f, centerY),
            strokeWidth = 2f,
        )

        for (i in 0 until TICK_COUNT) {
            val x = width * (0.02f + 0.96f * i / (TICK_COUNT - 1))
            val isCenter = i == CENTER_TICK
            val isMajor = i % 5 == 0
            val height = when {
                isCenter -> majorHeight * 1.25f
                isMajor -> majorHeight
                else -> minorHeight
            }
            drawLine(
                color = when {
                    isCenter -> needleColor
                    isMajor -> baseColor.copy(alpha = 0.6f)
                    else -> baseColor.copy(alpha = 0.35f)
                },
                start = Offset(x, centerY - height / 2),
                end = Offset(x, centerY + height / 2),
                strokeWidth = if (isCenter) 4f else if (isMajor) 3f else 2f,
                cap = StrokeCap.Round,
            )
        }

        val needleX = width * (needlePercent / 100f).coerceIn(0f, 1f)
        if (glow) {
            drawCircle(
                color = needleColor.copy(alpha = glowAlpha * 0.5f),
                radius = size.height * 0.28f,
                center = Offset(needleX, centerY),
            )
        }
        drawLine(
            color = needleColor,
            start = Offset(needleX, centerY - majorHeight * 0.95f),
            end = Offset(needleX, centerY + majorHeight * 0.95f),
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = needleColor,
            radius = 9f,
            center = Offset(needleX, centerY),
            style = Stroke(width = 3f),
        )

        val labels = listOf("-50", "-25", "0", "+25", "+50")
        labels.forEachIndexed { index, text ->
            val x = width * (0.02f + 0.96f * index / (labels.size - 1))
            val measured = textMeasurer.measure(
                text = text,
                style = labelStyle.copy(
                    color = if (index == 2) needleColor else baseColor,
                    fontWeight = if (index == 2) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            drawText(
                measured,
                topLeft = Offset(x - measured.size.width / 2f, centerY + majorHeight * 0.5f + 6.dp.toPx()),
            )
        }
    }
}
