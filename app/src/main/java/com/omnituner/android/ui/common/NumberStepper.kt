package com.omnituner.android.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun NumberStepper(
    headline: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(headline) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepeatingIconButton(label = "Decrease $headline", onStep = onDecrement) {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    // ListItem's trailing slot tints onSurfaceVariant, which reads as disabled.
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 80.dp),
                )
                RepeatingIconButton(label = "Increase $headline", onStep = onIncrement) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun RepeatingIconButton(
    label: String,
    onStep: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)

    LaunchedEffect(pressed) {
        if (pressed) {
            step()
            delay(REPEAT_DELAY_MS)
            while (pressed) {
                step()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    IconButton(
        onClick = {},
        interactionSource = interaction,
        modifier = Modifier.semantics {
            contentDescription = label
            onClick(label = label, action = {
                step()
                true
            })
        },
    ) {
        content()
    }
}

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 80L
