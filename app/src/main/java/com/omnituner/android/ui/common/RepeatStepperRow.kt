package com.omnituner.android.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Label + value + stepper buttons with press-and-hold auto-repeat.
 * A tap steps once (press); holding steps once immediately, then repeats after
 * [HOLD_DELAY_MS] every [REPEAT_INTERVAL_MS] until release.
 * onDelta receives +1/-1; clamping is the caller's responsibility.
 */
@Composable
fun RepeatStepperRow(
    label: String,
    valueText: String,
    onDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StepperButton(symbol = Icons.Filled.Remove, description = "Decrease $label") { onDelta(-1) }
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        StepperButton(symbol = Icons.Filled.Add, description = "Increase $label") { onDelta(1) }
    }
}

@Composable
private fun StepperButton(
    symbol: ImageVector,
    description: String,
    onStep: () -> Unit,
) {
    val interactionSource = MutableInteractionSource()
    val pressed = interactionSource.collectIsPressedAsState()

    LaunchedEffect(pressed.value) {
        if (pressed.value) {
            onStep()
            delay(HOLD_DELAY_MS)
            while (pressed.value) {
                onStep()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    IconButton(
        onClick = { },
        interactionSource = interactionSource,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(symbol, contentDescription = null)
    }
}

private const val HOLD_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 80L
