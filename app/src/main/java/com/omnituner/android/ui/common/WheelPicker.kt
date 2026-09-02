package com.omnituner.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnituner.android.R
import com.omnituner.android.ui.Haptics
import com.omnituner.android.ui.theme.currentWebPalette
import kotlinx.coroutines.launch
import kotlin.math.abs

private val WheelItemHeight = 44.dp
private const val WHEEL_VISIBLE_ROWS = 5
private const val WHEEL_REPEATS = 41

data class WheelOption(
    val label: String,
    val alt: String? = null,
)

@Composable
fun WheelPicker(
    options: List<WheelOption>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentWebPalette()
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val latestOnSelectedChange = rememberUpdatedState(onSelectedChange)
    val optionCount = options.size
    val middleStart = (WHEEL_REPEATS / 2) * optionCount

    var lastEmitted by remember { mutableIntStateOf(selectedIndex) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = middleStart + selectedIndex)

    val centeredReal by remember(optionCount) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val centered = layout.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            } ?: return@derivedStateOf null
            centered.index % optionCount
        }
    }

    LaunchedEffect(listState, optionCount) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && optionCount > 0) {
                val currentCopy = listState.firstVisibleItemIndex / optionCount
                val middleCopy = WHEEL_REPEATS / 2
                if (currentCopy != middleCopy) {
                    listState.scrollToItem(
                        listState.firstVisibleItemIndex + (middleCopy - currentCopy) * optionCount,
                        listState.firstVisibleItemScrollOffset,
                    )
                }
            }
        }
    }

    LaunchedEffect(centeredReal) {
        val real = centeredReal ?: return@LaunchedEffect
        if (real != lastEmitted) {
            lastEmitted = real
            latestOnSelectedChange.value(real)
            if (listState.isScrollInProgress) haptics.light()
        }
    }

    val scope = rememberCoroutineScope()
    val currentCentered by rememberUpdatedState(centeredReal)

    Box(
        modifier = modifier
            .height(WheelItemHeight * WHEEL_VISIBLE_ROWS)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Previous option") {
                        val real = currentCentered
                        if (real != null) {
                            scope.launch {
                                listState.animateScrollToItem(
                                    middleStart + ((real - 1 + optionCount) % optionCount),
                                )
                            }
                            true
                        } else {
                            false
                        }
                    },
                    CustomAccessibilityAction("Next option") {
                        val real = currentCentered
                        if (real != null) {
                            scope.launch {
                                listState.animateScrollToItem(
                                    middleStart + ((real + 1) % optionCount),
                                )
                            }
                            true
                        } else {
                            false
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceHigh),
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(vertical = WheelItemHeight * (WHEEL_VISIBLE_ROWS / 2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = optionCount * WHEEL_REPEATS) { virtual ->
                val real = virtual % optionCount
                val option = options[real]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            scope.launch { listState.animateScrollToItem(virtual) }
                        }
                        .graphicsLayer {
                            val layout = listState.layoutInfo
                            val viewportCenter =
                                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                            val info = layout.visibleItemsInfo.firstOrNull { it.index == virtual }
                            alpha = if (info == null) {
                                0f
                            } else {
                                val distance =
                                    abs(info.offset + info.size / 2f - viewportCenter) /
                                        WheelItemHeight.roundToPx()
                                (1f - distance / (WHEEL_VISIBLE_ROWS / 2f + 0.5f))
                                    .coerceIn(0.2f, 1f)
                            }
                        }
                        .semantics { contentDescription = option.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = option.label,
                            style = if (option.alt == null) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.alt != null) {
                            Text(
                                text = option.alt,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WheelItemHeight * (WHEEL_VISIBLE_ROWS / 2))
                .background(
                    Brush.verticalGradient(listOf(palette.surfaceLow, Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WheelItemHeight * (WHEEL_VISIBLE_ROWS / 2))
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, palette.surfaceLow)),
                ),
        )
    }
}

@Composable
fun WheelSelectSheet(
    title: String,
    options: List<WheelOption>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentWebPalette()
    var pending by remember { mutableIntStateOf(selectedIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(palette.surfaceLow)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.borderMedium),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WheelSheetButton(
                        background = palette.surfaceHigh,
                        iconRes = R.drawable.tabler_x,
                        iconTint = palette.text,
                        contentDescription = "Cancel",
                        onClick = onDismiss,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    WheelSheetButton(
                        background = palette.scaleAccent,
                        iconRes = R.drawable.tabler_check,
                        iconTint = palette.scaleAccentInk,
                        contentDescription = "Confirm",
                        onClick = { onConfirm(pending) },
                    )
                }
                WheelPicker(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectedChange = { pending = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WheelSheetButton(
    background: Color,
    iconRes: Int,
    iconTint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun WheelSelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueDotColor: Color? = null,
) {
    val palette = currentWebPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (valueDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(valueDotColor, CircleShape),
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.tabler_chevron_down),
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
