package com.omnituner.android.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
private val WheelDismissDistance = 120.dp

data class WheelOption(
    val label: String,
    val alt: String? = null,
    val dotColor: Color? = null,
)

data class WheelSection(
    val label: String,
    val options: List<WheelOption>,
)

private sealed interface WheelRow {
    data class Header(val label: String) : WheelRow
    data class Option(val option: WheelOption, val index: Int) : WheelRow
}

private fun WheelRow.label(): String = when (this) {
    is WheelRow.Header -> label
    is WheelRow.Option -> option.label
}

@Composable
fun WheelPicker(
    options: List<WheelOption>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemTrailing: (@Composable (Int) -> Unit)? = null,
) {
    val rows = remember(options) {
        options.mapIndexed { index, option -> WheelRow.Option(option, index) }
    }
    WheelList(
        rows = rows,
        selectedOptionIndex = selectedIndex,
        onSelectedChange = onSelectedChange,
        modifier = modifier,
        itemTrailing = itemTrailing,
    )
}

@Composable
private fun WheelList(
    rows: List<WheelRow>,
    selectedOptionIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemTrailing: (@Composable (Int) -> Unit)? = null,
) {
    if (rows.none { it is WheelRow.Option }) return
    val palette = currentWebPalette()
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val latestOnSelectedChange = rememberUpdatedState(onSelectedChange)
    val maxOptionIndex = rows.filterIsInstance<WheelRow.Option>().maxOf { it.index }
    val initialRow = rows.indexOfFirst {
        it is WheelRow.Option && it.index == selectedOptionIndex.coerceIn(0, maxOptionIndex)
    }

    var lastEmitted by remember {
        mutableIntStateOf(selectedOptionIndex.coerceIn(0, maxOptionIndex))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)

    val centeredRow by remember(rows) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val centered = layout.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            } ?: return@derivedStateOf null
            centered.index.coerceIn(0, rows.lastIndex)
        }
    }

    val scope = rememberCoroutineScope()
    val currentCenteredRow by rememberUpdatedState(centeredRow)

    fun adjacentOptionRow(from: Int, step: Int): Int {
        var i = from + step
        while (i in rows.indices) {
            if (rows[i] is WheelRow.Option) return i
            i += step
        }
        return -1
    }

    LaunchedEffect(centeredRow) {
        val position = centeredRow ?: return@LaunchedEffect
        val row = rows.getOrNull(position) ?: return@LaunchedEffect
        if (row is WheelRow.Option && row.index != lastEmitted) {
            lastEmitted = row.index
            latestOnSelectedChange.value(row.index)
            if (listState.isScrollInProgress) haptics.light()
        }
    }

    LaunchedEffect(rows) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) return@collect
                val position = centeredRow ?: return@collect
                val row = rows.getOrNull(position) ?: return@collect
                if (row !is WheelRow.Header) return@collect
                val next = position + 1
                val bounceTo = if (next < rows.size && rows[next] is WheelRow.Option) {
                    next
                } else {
                    adjacentOptionRow(position, -1)
                }
                if (bounceTo >= 0) {
                    listState.animateScrollToItem(bounceTo)
                }
            }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val bounded = constraints.maxHeight != Constraints.Infinity
        val containerHeightPx = if (bounded) {
            constraints.maxHeight
        } else {
            with(density) { (WheelItemHeight * WHEEL_VISIBLE_ROWS).roundToPx() }
        }
        val edgePad = with(density) {
            ((containerHeightPx - WheelItemHeight.roundToPx()) / 2).coerceAtLeast(0).toDp()
        }
        val fill = if (bounded) {
            Modifier.fillMaxSize()
        } else {
            Modifier.height(WheelItemHeight * WHEEL_VISIBLE_ROWS)
        }

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
            contentPadding = PaddingValues(vertical = edgePad),
            modifier = fill,
        ) {
            items(count = rows.size) { position ->
                val row = rows[position]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .then(
                            if (row is WheelRow.Option) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    scope.launch { listState.animateScrollToItem(position) }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            val layout = listState.layoutInfo
                            val viewportCenter =
                                (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                            val info = layout.visibleItemsInfo.firstOrNull { it.index == position }
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
                        .semantics { contentDescription = row.label() },
                    contentAlignment = Alignment.Center,
                ) {
                    when (row) {
                        is WheelRow.Header -> Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        is WheelRow.Option -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (row.option.dotColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(row.option.dotColor, CircleShape),
                                        )
                                    }
                                    Text(
                                        text = row.option.label,
                                        style = if (row.option.alt == null) {
                                            MaterialTheme.typography.labelLarge
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        color = palette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (row.option.alt != null) {
                                    Text(
                                        text = row.option.alt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.dim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (itemTrailing != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp),
                                ) {
                                    itemTrailing(row.index)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(edgePad)
                .background(
                    Brush.verticalGradient(listOf(palette.surfaceLow, Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(edgePad)
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
    itemTrailing: (@Composable (Int) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val rows = remember(options) {
        options.mapIndexed { index, option -> WheelRow.Option(option, index) }
    }
    WheelSheet(
        title = title,
        rows = rows,
        selectedOptionIndex = selectedIndex,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        itemTrailing = itemTrailing,
        footer = footer,
    )
}

@Composable
fun WheelSelectSheet(
    title: String,
    sections: List<WheelSection>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val rows = remember(sections) {
        var nextIndex = 0
        sections.flatMap { section ->
            listOf(WheelRow.Header(section.label)) +
                section.options.map { option ->
                    WheelRow.Option(option, nextIndex++)
                }
        }
    }
    WheelSheet(
        title = title,
        rows = rows,
        selectedOptionIndex = selectedIndex,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        itemTrailing = null,
        footer = footer,
    )
}

@Composable
private fun WheelSheet(
    title: String,
    rows: List<WheelRow>,
    selectedOptionIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    itemTrailing: (@Composable (Int) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = currentWebPalette()
    var pending by remember { mutableIntStateOf(selectedOptionIndex) }
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistance = with(density) { WheelDismissDistance.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 1f - (dragY.value / (dismissDistance * 1.5f)).coerceIn(0f, 1f)
                    }
                    .background(palette.backdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .graphicsLayer { translationY = dragY.value }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(palette.surfaceLow)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragY.snapTo((dragY.value + dragAmount).coerceAtLeast(0f))
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (dragY.value > dismissDistance) {
                                        dragY.animateTo(dragY.value + 600f, tween(160))
                                        onDismiss()
                                    } else {
                                        dragY.animateTo(
                                            0f,
                                            spring(stiffness = Spring.StiffnessMediumLow),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragY.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            },
                        )
                    }
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
                WheelList(
                    rows = rows,
                    selectedOptionIndex = selectedOptionIndex,
                    onSelectedChange = { pending = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    itemTrailing = itemTrailing,
                )
                if (footer != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    footer()
                }
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
