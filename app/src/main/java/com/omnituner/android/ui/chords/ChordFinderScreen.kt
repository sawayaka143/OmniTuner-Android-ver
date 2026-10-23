package com.omnituner.android.ui.chords

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.min
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.ChordSettings
import com.omnituner.android.data.DiagramLabels
import com.omnituner.android.data.LibraryState
import com.omnituner.android.data.VoicingView
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.android.ui.common.SegmentedRow
import com.omnituner.android.ui.common.TopBarContent
import com.omnituner.android.ui.common.PullDownMenu
import com.omnituner.android.ui.common.PullDownMenuDivider
import com.omnituner.android.ui.common.PullDownMenuItem
import com.omnituner.android.ui.common.PullDownMenuSection
import com.omnituner.android.ui.common.RepeatingIconButton
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalStatusColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.core.theory.BadgeKind
import com.omnituner.core.theory.DiatonicBadge
import com.omnituner.core.theory.ParsedTuning
import com.omnituner.core.theory.VoicingShape
import com.omnituner.core.theory.flatsForPc
import com.omnituner.core.theory.keyName
import com.omnituner.core.theory.pcName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COPIED_FEEDBACK_MS = 1100L
private val MaxCardWidth = 380.dp
// How much of the next chord card shows at the edge, so the row reads as swipeable.
private val CardPeek = 32.dp
// Drops the 4 dp progress bar from the card's bottom edge to the middle of the 16 dp gap below it.
private val ProgressGapOffset = 10.dp

@Composable
fun ChordFinderScreen(viewModel: ChordFinderViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val tabs = @Composable {
        SegmentedRow(
            options = ChordsTab.entries,
            selected = ui.tab,
            label = { if (it == ChordsTab.Voicings) "Voicings" else "Key finder" },
            onSelect = viewModel::selectTab,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    // Upright, the display options and the overflow (Copy tab / Clear) live in the top bar while
    // there are voicings to act on; in landscape the rail replaces the bar and the input card
    // carries them.
    // They grow in beside the Settings button when there are results, rather than popping in.
    TopBarContent(
        actions = {
            AnimatedVisibility(
                visible = ui.tab == ChordsTab.Voicings && ui.results != null,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start) + scaleIn(),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start) + scaleOut(),
            ) {
                Row {
                    val barButton: MenuButton = { onClick, icon -> FilledTonalIconButton(onClick = onClick, content = icon) }
                    DisplayOptionsMenu(ui.settings, viewModel, barButton)
                    MoreMenu(viewModel, barButton)
                }
            }
        },
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: the input on the left, what it produces on the right. The chord cards are
            // sized to the pane's height, and the key and display options move into the input card,
            // so the right side is nothing but chords.
            val inputWidth = (maxWidth * InputPaneShare).coerceAtMost(MaxInputPaneWidth)
            Row(modifier = Modifier.fillMaxSize()) {
                Pane(Modifier.width(inputWidth)) {
                    tabs()
                    when (ui.tab) {
                        ChordsTab.Voicings -> VoicingsInput(ui, viewModel, withResultsBar = true)
                        ChordsTab.KeyFinder -> KeyFinderInput(ui, viewModel)
                    }
                }
                when (ui.tab) {
                    ChordsTab.Voicings -> BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp),
                    ) {
                        VoicingsResults(ui, viewModel, fitHeight = maxHeight)
                    }
                    ChordsTab.KeyFinder -> Pane(Modifier.weight(1f)) { KeyFinderResultCard(ui.keyAnalysis) }
                }
            }
        } else {
            Pane(Modifier) {
                tabs()
                when (ui.tab) {
                    ChordsTab.Voicings -> {
                        VoicingsInput(ui, viewModel)
                        VoicingsResults(ui, viewModel)
                    }
                    ChordsTab.KeyFinder -> KeyFinderTab(ui, viewModel)
                }
            }
        }
    }
}

private const val InputPaneShare = 0.45f
private val MaxInputPaneWidth = 420.dp

/** A scrolling column of the page's blocks; each block pads its own sides. */
@Composable
private fun Pane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun VoicingsInput(ui: ChordFinderUiState, viewModel: ChordFinderViewModel, withResultsBar: Boolean = false) {
    var tuningSheet by rememberSaveable { mutableStateOf(false) }
    var tonicSheet by rememberSaveable { mutableStateOf(false) }
    val library = ui.library

    // The progress bar floats in the gap under the input card instead of taking a row of its own,
    // so starting or finishing a generate never shifts the results below.
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            if (library != null) {
                PickerRow(
                    headline = "Tuning",
                    value = "${library.instrument.label} · ${library.tuning.label}",
                    onClick = { tuningSheet = true },
                )
            }
            PickerRow(
                headline = "Tonic",
                value = pcName(ui.tonicPc, flatsForPc(ui.tonicPc)),
                onClick = { tonicSheet = true },
            )
            ChordField(
                value = ui.progressionInput,
                onValueChange = viewModel::setProgressionInput,
                count = ui.progressionCount,
                onDone = viewModel::generate,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            // Landscape: the key and display options sit right under the input, ahead of Generate,
            // so they stay in view on a short screen while the buttons can scroll off. The bar is
            // there before the first generate too, so Generate never jumps down under the finger.
            if (withResultsBar) {
                HorizontalDivider()
                ResultsHeader(
                    results = ui.results,
                    settings = ui.settings,
                    viewModel = viewModel,
                    tonal = true,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider()
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (withResultsBar) 8.dp else 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(shape = SmoothShape.Full, onClick = viewModel::generate, modifier = Modifier.weight(1f)) {
                    Text("Generate")
                }
                FilledTonalButton(shape = SmoothShape.Full, onClick = viewModel::shuffle) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
                FilledTonalButton(
                    shape = SmoothShape.Full,
                    onClick = viewModel::playProgression,
                    enabled = ui.results != null,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play progression", modifier = Modifier.size(18.dp))
                }
            }
        }
        if (ui.generating) {
            // offset() moves it without affecting layout.
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .offset(y = ProgressGapOffset),
            )
        }
    }

    if (tuningSheet && library != null) TuningSheet(library, viewModel, onDismiss = { tuningSheet = false })
    if (tonicSheet) {
        OptionSheet(
            title = "Tonic",
            options = (0 until 12).map { PickerOption(it, pcName(it, flatsForPc(it))) },
            selected = ui.tonicPc,
            onSelect = { pc ->
                viewModel.setTonic(pc)
                tonicSheet = false
            },
            onDismiss = { tonicSheet = false },
        )
    }
}

/**
 * The chord cards (with the key and display options over them unless [fitHeight] is set, when
 * landscape has moved those into the input card and the cards fill that height).
 */
@Composable
private fun VoicingsResults(ui: ChordFinderUiState, viewModel: ChordFinderViewModel, fitHeight: Dp? = null) {
    val results = ui.results
    if (results == null) {
        if (!ui.generating) EmptyResults()
    } else {
        if (fitHeight == null) ResultsHeader(results = results, settings = ui.settings, viewModel = viewModel)
        ChordCards(results, ui.settings, onPlay = viewModel::playShape, fitHeight = fitHeight)
    }
}

@Composable
private fun TuningSheet(library: LibraryState, viewModel: ChordFinderViewModel, onDismiss: () -> Unit) {
    val options = library.catalog.flatMap { entry ->
        entry.tunings.map { tuning ->
            PickerOption(
                key = entry.instrument.id to tuning.id,
                label = tuning.label,
                supporting = tuning.strings.joinToString(" ") { it.name },
                section = "${entry.instrument.label} ${entry.instrument.stringCount}-string",
            )
        }
    }
    OptionSheet(
        title = "Tuning",
        options = options,
        selected = library.instrument.id to library.tuning.id,
        onSelect = { (instrumentId, tuningId) ->
            viewModel.selectTuning(instrumentId, tuningId)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChordField(
    value: String,
    onValueChange: (String) -> Unit,
    count: ParseCount,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val countColor = when {
        count.total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        count.readable < count.total -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Chords, comma-separated") },
        singleLine = true,
        supportingText = {
            Text(
                text = if (count.total == 0) "0 chords parsed" else "${count.total} chords parsed · ${count.readable} readable",
                color = countColor,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyResults() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("No voicings yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Enter a chord progression and tap Generate. Matching fingerings show up here as a neck diagram or tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Sits above the chord cards: the detected key on the left and, in the landscape input card
 * ([tonal]), the display options and an overflow with Copy tab / Clear. Upright, those two live in
 * the top bar instead (see [DisplayOptionsMenu] and [MoreMenu]), so only the key shows here; it
 * appears once there is something to show.
 */
@Composable
private fun ResultsHeader(
    // Null in the landscape input card before the first generate: the options show, the key not yet.
    results: GeneratedVoicings?,
    settings: ChordSettings,
    viewModel: ChordFinderViewModel,
    modifier: Modifier = Modifier,
    // Standalone tonal buttons, for the bar inside the landscape input card.
    tonal: Boolean = false,
) {
    // The card is the tonal grey itself, so the buttons take the page color to stand out, as on the
    // tuner and metronome panels.
    val button: MenuButton = { onClick, icon ->
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            content = icon,
        )
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tonal) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            results?.key?.let { key ->
                KeyRow(keyName(key.tonicName, key.mode), key.alternatives.map { keyName(it.tonicName, it.mode) })
            }
        }
        if (tonal) {
            DisplayOptionsMenu(settings, viewModel, button)
            MoreMenu(viewModel, button)
        }
    }
}

private typealias MenuButton = @Composable (onClick: () -> Unit, icon: @Composable () -> Unit) -> Unit

@Composable
private fun DisplayOptionsMenu(settings: ChordSettings, viewModel: ChordFinderViewModel, button: MenuButton) {
    var optionsOpen by remember { mutableStateOf(false) }
    Box {
        button({ optionsOpen = true }) {
            Icon(Icons.Filled.Tune, contentDescription = "Display options", tint = MaterialTheme.colorScheme.onSurface)
        }
        PullDownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
            PullDownMenuSection("View")
            VoicingView.entries.forEach { view ->
                PullDownMenuItem(
                    text = if (view == VoicingView.Fretboard) "Fretboard" else "Tab",
                    selected = settings.view == view,
                    onClick = { viewModel.setView(view) },
                )
            }
            PullDownMenuDivider()
            PullDownMenuSection("Labels")
            DiagramLabels.entries.forEach { labels ->
                PullDownMenuItem(
                    text = if (labels == DiagramLabels.Notes) "Notes" else "Degrees",
                    selected = settings.labels == labels,
                    onClick = { viewModel.setLabels(labels) },
                )
            }
            PullDownMenuDivider()
            PullDownMenuSection("Fingerings per chord")
            // A stepper rather than a list: the count runs 1-12. Lined up with the item labels above.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RepeatingIconButton(label = "Fewer fingerings", onStep = { viewModel.changeVoicingCount(-1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("${settings.voicingCount}", style = MaterialTheme.typography.titleMedium)
                RepeatingIconButton(label = "More fingerings", onStep = { viewModel.changeVoicingCount(1) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MoreMenu(viewModel: ChordFinderViewModel, button: MenuButton) {
    var moreOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val onCopy = {
        val text = viewModel.tabText()
        if (text != null) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Chord tab", text)))
                copied = true
                delay(COPIED_FEEDBACK_MS)
                copied = false
            }
        }
    }
    Box {
        button({ moreOpen = true }) {
            // Brief check after copying, since the menu has already closed by then.
            Icon(
                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.MoreVert,
                contentDescription = if (copied) "Tab copied" else "More",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        PullDownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
            PullDownMenuItem(
                text = "Copy tab",
                icon = Icons.Filled.ContentCopy,
                onClick = {
                    moreOpen = false
                    onCopy()
                },
            )
            PullDownMenuItem(
                text = "Clear",
                icon = Icons.Filled.Clear,
                onClick = {
                    moreOpen = false
                    viewModel.clear()
                },
            )
        }
    }
}

/**
 * The detected key; with close alternatives, a chip that reveals them on the same line beside it
 * (one line, ellipsized), so opening it never pushes the chord cards down.
 */
@Composable
private fun KeyRow(key: String, alternatives: List<String>) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    if (alternatives.isEmpty()) {
        Text("Key: $key", style = MaterialTheme.typography.titleSmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = open, onClick = { open = !open }, label = { Text("Key: $key", maxLines = 1) })
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "or ${alternatives.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChordCards(
    results: GeneratedVoicings,
    settings: ChordSettings,
    onPlay: (VoicingShape) -> Unit,
    fitHeight: Dp? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val full = maxWidth - 32.dp
        // Fitted (landscape), every chord takes the whole pane, its diagram widening its fret window
        // to fill it, and a swipe pages to the next one (snapped, so a card never stops half shown).
        val cardWidth: Dp = if (fitHeight != null || results.chords.size == 1) {
            full
        } else {
            min(full - CardPeek, MaxCardWidth)
        }
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            flingBehavior = if (fitHeight != null) rememberSnapFlingBehavior(listState) else ScrollableDefaults.flingBehavior(),
            modifier = if (fitHeight != null) Modifier.height(fitHeight) else Modifier,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            itemsIndexed(results.chords) { index, entry ->
                ChordCard(
                    index,
                    entry,
                    results.tuning,
                    settings,
                    onPlay,
                    fillHeight = fitHeight != null,
                    modifier = Modifier.width(cardWidth).then(if (fitHeight != null) Modifier.fillMaxHeight() else Modifier),
                )
            }
        }
    }
}

@Composable
private fun ChordCard(
    index: Int,
    entry: ChordEntry,
    tuning: ParsedTuning,
    settings: ChordSettings,
    onPlay: (VoicingShape) -> Unit,
    modifier: Modifier = Modifier,
    // Fitted to a height (landscape): the name stays put and the fingerings scroll under it.
    fillHeight: Boolean = false,
) {
    val status = LocalStatusColors.current
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val chord = entry.chord
            if (chord == null) {
                Text("${index + 1}. ${entry.token}", style = MaterialTheme.typography.titleMedium)
                Text(entry.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val flats = chord.flats || tuning.flats
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${chord.symbol}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    entry.badge?.let { NumeralChip(it) }
                }
                Text(chordTonesLabel(chord, flats), style = MaterialTheme.typography.bodySmall, color = status.info)
            }
            if (entry.shapes.isEmpty()) {
                Text(
                    text = "No voicings fit these rules. Try a different tuning or chord spelling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = status.warn,
                )
            }
            val fingering = @Composable { k: Int, shape: VoicingShape, blockModifier: Modifier ->
                Column(modifier = blockModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Only worth naming when there are several to tell apart.
                    if (entry.shapes.size > 1) {
                        Text(
                            text = "FINGERING ${k + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val playable = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Play") { onPlay(shape) }
                    // Fitted, the diagram gets what is left of its block under the label and keeps
                    // its proportions inside it, showing as many frets as make it fill that space
                    // (centred, in case a fret's width is left over).
                    BoxWithConstraints(
                        modifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier,
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val columns = if (fillHeight && maxHeight > 0.dp) {
                            neckDiagramColumnsFor(maxWidth / maxHeight, tuning.midi.size)
                        } else {
                            0
                        }
                        when (settings.view) {
                            VoicingView.Fretboard -> NeckDiagram(
                                frets = shape.frets,
                                tuning = tuning,
                                symbol = chord.symbol,
                                rootPc = chord.rootPc,
                                index = k,
                                labels = settings.labels,
                                flats = flats,
                                modifier = playable,
                                minColumns = columns,
                            )
                            VoicingView.Tab -> TabBlock(shape, tuning, playable)
                        }
                    }
                }
            }
            if (fillHeight) {
                // Measured, not estimated: every fingering block is exactly the height left under
                // the chord name, so each one fits the card whole and the rest scroll in one by one.
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val block = maxHeight
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        entry.shapes.forEachIndexed { k, shape -> fingering(k, shape, Modifier.height(block)) }
                    }
                }
            } else {
                entry.shapes.forEachIndexed { k, shape -> fingering(k, shape, Modifier) }
            }
        }
    }
}

@Composable
private fun NumeralChip(badge: DiatonicBadge) {
    val color = when (badge.kind) {
        BadgeKind.Good -> MaterialTheme.colorScheme.onSurfaceVariant
        BadgeKind.Warn -> LocalAccentColors.current.accent
        BadgeKind.Borrowed -> LocalStatusColors.current.info
        BadgeKind.Bad -> LocalStatusColors.current.warn
    }
    val border = if (badge.kind == BadgeKind.Good) MaterialTheme.colorScheme.outlineVariant else color
    Surface(
        shape = SmoothShape.Full,
        color = Color.Transparent,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = badge.numeral,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TabBlock(shape: VoicingShape, tuning: ParsedTuning, modifier: Modifier = Modifier) {
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val dim = label.copy(alpha = 0.5f)
    val text = MaterialTheme.colorScheme.onSurface
    val lines = buildAnnotatedString {
        tabRows(shape, tuning).forEachIndexed { i, row ->
            if (i > 0) append('\n')
            withStyle(SpanStyle(color = label)) { append(row.label) }
            withStyle(SpanStyle(color = dim)) { append("|" + "-".repeat(row.padLeft)) }
            withStyle(SpanStyle(color = if (row.muted) dim else text, fontWeight = if (row.muted) FontWeight.Normal else FontWeight.SemiBold)) {
                append(row.symbol)
            }
            withStyle(SpanStyle(color = dim)) { append("-".repeat(row.padRight) + "|") }
        }
    }
    Text(
        text = lines,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        modifier = modifier.fillMaxWidth().padding(4.dp),
    )
}

@Composable
private fun KeyFinderTab(ui: ChordFinderUiState, viewModel: ChordFinderViewModel) {
    KeyFinderCard {
        KeyFinderFields(ui, viewModel)
        KeyFinderResult(ui.keyAnalysis)
    }
}

// Landscape splits the card: the input on the left, the detected key in its own card on the right.
@Composable
private fun KeyFinderInput(ui: ChordFinderUiState, viewModel: ChordFinderViewModel) {
    KeyFinderCard { KeyFinderFields(ui, viewModel) }
}

@Composable
private fun KeyFinderResultCard(analysis: KeyAnalysis) {
    KeyFinderCard { KeyFinderResult(analysis) }
}

@Composable
private fun KeyFinderCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun KeyFinderFields(ui: ChordFinderUiState, viewModel: ChordFinderViewModel) {
    Text(
        text = "Enter a chord progression and the matching key is detected live.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChordField(value = ui.keyInput, onValueChange = viewModel::setKeyInput, count = ui.keyAnalysis.count)
}

@Composable
private fun KeyFinderResult(analysis: KeyAnalysis) {
    val key = analysis.key
    when {
        key != null -> {
            val color = when (key.confidence) {
                "strong" -> MaterialTheme.colorScheme.tertiary
                "moderate" -> LocalAccentColors.current.accent
                else -> LocalStatusColors.current.warn
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${key.tonicName} ${key.mode}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    text = key.confidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val names = key.alternatives.joinToString(", ") { "${it.tonicName} ${it.mode}" }
            if (names.isNotEmpty()) {
                Text(
                    text = if (key.confidence == "strong") "Also fits: $names" else "Not sure. Also possible: $names",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        analysis.count.total > 0 -> Text(
            text = "No readable chords. Check the spelling.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalStatusColors.current.warn,
        )
        else -> Text(
            text = "Type chords to detect the key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
