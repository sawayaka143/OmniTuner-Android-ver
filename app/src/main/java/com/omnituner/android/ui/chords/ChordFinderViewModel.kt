package com.omnituner.android.ui.chords

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.data.ChordSettings
import com.omnituner.android.data.DiagramLabels
import com.omnituner.android.data.LibraryState
import com.omnituner.android.data.VoicingView
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.data.PROGRESSION_PRESETS
import com.omnituner.core.data.Tuning
import com.omnituner.core.theory.ChordParseResult
import com.omnituner.core.theory.DetectedKey
import com.omnituner.core.theory.DiatonicBadge
import com.omnituner.core.theory.MAX_VOICING_COUNT
import com.omnituner.core.theory.MIN_VOICING_COUNT
import com.omnituner.core.theory.ParsedChord
import com.omnituner.core.theory.ParsedTuning
import com.omnituner.core.theory.TuningParseResult
import com.omnituner.core.theory.VoicingShape
import com.omnituner.core.theory.computeBadgeForPc
import com.omnituner.core.theory.degreeToChordSymbol
import com.omnituner.core.theory.degreesToProgression
import com.omnituner.core.theory.detectKey
import com.omnituner.core.theory.flatsForPc
import com.omnituner.core.theory.keyName
import com.omnituner.core.theory.midiName
import com.omnituner.core.theory.parseChord
import com.omnituner.core.theory.parseTuning
import com.omnituner.core.theory.pcName
import com.omnituner.core.theory.searchChord
import com.omnituner.core.theory.tokenizeProgression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

private const val CHORD_DURATION_SECONDS = 1.2
private const val PROGRESSION_GAP_MS = 700L
private const val VOICING_NOTE_GAP_MS = 90L
private const val MIN_SHUFFLE_CHORDS = 3
private const val DEFAULT_PROGRESSION = "C, G, Am, F"

enum class ChordsTab { Voicings, KeyFinder }

/** One token of the progression: its chord (or why it isn't one), fingerings and key badge. */
data class ChordEntry(
    val token: String,
    val chord: ParsedChord?,
    val error: String?,
    val shapes: List<VoicingShape>,
    val badge: DiatonicBadge?,
)

data class GeneratedVoicings(
    val tuning: ParsedTuning,
    val chords: List<ChordEntry>,
    val key: DetectedKey?,
)

/** How many tokens a chord field holds and how many of them read as chords. */
data class ParseCount(val total: Int, val readable: Int)

data class KeyAnalysis(val count: ParseCount, val key: DetectedKey?)

data class ChordFinderUiState(
    val tab: ChordsTab = ChordsTab.Voicings,
    val library: LibraryState? = null,
    val settings: ChordSettings = ChordSettings(),
    val progressionInput: String = DEFAULT_PROGRESSION,
    val progressionCount: ParseCount = ParseCount(0, 0),
    val tonicPc: Int = 0,
    val generating: Boolean = false,
    // Fingerings already cut to settings.voicingCount.
    val results: GeneratedVoicings? = null,
    val keyInput: String = "",
    val keyAnalysis: KeyAnalysis = KeyAnalysis(ParseCount(0, 0), null),
)

private data class Inputs(
    val tab: ChordsTab = ChordsTab.Voicings,
    val progression: String = DEFAULT_PROGRESSION,
    val tonicPc: Int = 0,
    val keyInput: String = "",
)

private data class Generation(val running: Boolean = false, val result: GeneratedVoicings? = null)

class ChordFinderViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val library = container.instruments
    private val settings = container.chords
    private val noteAudio = container.noteAudio
    private val inputs = MutableStateFlow(Inputs())
    private val generation = MutableStateFlow(Generation())
    private var generateJob: Job? = null
    private var playJob: Job? = null

    val ui: StateFlow<ChordFinderUiState> = combine(
        inputs,
        generation,
        settings.flow,
        library.state,
    ) { current, gen, chordSettings, libraryState ->
        ChordFinderUiState(
            tab = current.tab,
            library = libraryState,
            settings = chordSettings,
            progressionInput = current.progression,
            progressionCount = countReadable(current.progression, current.tonicPc),
            tonicPc = current.tonicPc,
            generating = gen.running,
            results = gen.result?.let { result ->
                result.copy(chords = result.chords.map { it.copy(shapes = it.shapes.take(chordSettings.voicingCount)) })
            },
            keyInput = current.keyInput,
            keyAnalysis = analyseKey(current.keyInput),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChordFinderUiState())

    init {
        generate()
        // A new tuning re-voices whatever is on screen.
        viewModelScope.launch {
            library.state.map { it.tuning }.distinctUntilChanged().drop(1).collect {
                if (generation.value.result != null) generate()
            }
        }
    }

    fun selectTab(tab: ChordsTab) = inputs.update { it.copy(tab = tab) }

    fun setProgressionInput(text: String) = inputs.update { it.copy(progression = text) }

    fun setTonic(pc: Int) = inputs.update { it.copy(tonicPc = pc) }

    fun setKeyInput(text: String) = inputs.update { it.copy(keyInput = text) }

    fun selectTuning(instrumentId: String, tuningId: String) {
        viewModelScope.launch { library.select(instrumentId, tuningId) }
    }

    fun changeVoicingCount(delta: Int) = updateSettings {
        it.copy(voicingCount = (it.voicingCount + delta).coerceIn(MIN_VOICING_COUNT, MAX_VOICING_COUNT))
    }

    fun setView(view: VoicingView) = updateSettings { it.copy(view = view) }

    fun setLabels(labels: DiagramLabels) = updateSettings { it.copy(labels = labels) }

    /** A random preset in a random key, written into the field and voiced. */
    fun shuffle() {
        val preset = PROGRESSION_PRESETS[Random.nextInt(PROGRESSION_PRESETS.size)]
        val tonicPc = Random.nextInt(12)
        val chords = degreesToProgression(preset.degrees, tonicPc, flatsForPc(tonicPc))
        if (chords.size < MIN_SHUFFLE_CHORDS) return
        inputs.update { it.copy(progression = chords.joinToString(", "), tonicPc = tonicPc) }
        generate()
    }

    fun clear() {
        generateJob?.cancel()
        generation.value = Generation()
    }

    /** Voices every chord of the progression off the main thread; long tunings take a moment. */
    fun generate() {
        val tuning = parsedTuningOf(library.state.value.tuning) ?: return
        val current = inputs.value
        val tokens = tokenizeProgression(current.progression)
        if (tokens.isEmpty()) return
        generateJob?.cancel()
        generation.update { it.copy(running = true) }
        generateJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { voice(tokens, current.tonicPc, tuning) }
            generation.value = Generation(running = false, result = result)
        }
    }

    fun playShape(shape: VoicingShape) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            for (note in shape.sounding) {
                noteAudio.playNote(note.midi)
                delay(VOICING_NOTE_GAP_MS)
            }
        }
    }

    /** Strums each chord's first fingering in turn. */
    fun playProgression() {
        val shapes = ui.value.results?.chords?.mapNotNull { it.shapes.firstOrNull() } ?: return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            for (shape in shapes) {
                for (note in shape.sounding) {
                    noteAudio.playNote(note.midi, durationSeconds = CHORD_DURATION_SECONDS)
                }
                delay(PROGRESSION_GAP_MS)
            }
        }
    }

    /** The visible results as plain-text tab, the same layout the web app copies. */
    fun tabText(): String? {
        val result = ui.value.results ?: return null
        val tuning = result.tuning
        val lines = mutableListOf<String>()
        val key = result.key?.let { keyName(it.tonicName, it.mode) } ?: "—"
        lines += "Chord finder — tuning ${tuning.labels.joinToString(" ")} — key $key"
        lines += ""
        for (entry in result.chords) {
            val chord = entry.chord
            if (chord == null) {
                lines += "=== ${entry.token} ==="
                lines += "!! ${entry.error}"
                lines += ""
                continue
            }
            entry.shapes.forEachIndexed { k, shape ->
                lines += "=== ${chord.symbol} - Fingering ${k + 1} ==="
                for (row in tabRows(shape, tuning)) {
                    lines += "${row.label}|${"-".repeat(row.padLeft)}${row.symbol}${"-".repeat(row.padRight)}|"
                }
                lines += ""
            }
        }
        return lines.joinToString("\n")
    }

    override fun onCleared() {
        playJob?.cancel()
        generateJob?.cancel()
    }

    private fun updateSettings(transform: (ChordSettings) -> ChordSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private suspend fun voice(tokens: List<String>, tonicPc: Int, tuning: ParsedTuning): GeneratedVoicings {
        val parses = tokens.map { resolveToken(it, tonicPc) }
        val chords = parses.mapNotNull { (it as? ChordParseResult.Ok)?.chord }
        val key = if (chords.isNotEmpty()) detectKey(chords, tuningFlats = tuning.flats) else null
        val entries = tokens.zip(parses).map { (token, parse) ->
            coroutineContext.ensureActive()
            when (parse) {
                is ChordParseResult.Error -> ChordEntry(token, null, parse.error, emptyList(), null)
                is ChordParseResult.Ok -> {
                    val badge = key?.let {
                        val useFlats = chords.any { c -> c.flats } || tuning.flats || flatsForPc(it.tonicPc)
                        computeBadgeForPc(parse.chord, it.tonicPc, it.mode, tuning.flats, useFlats)
                    }
                    ChordEntry(token, parse.chord, null, searchChord(tuning, parse.chord, MAX_VOICING_COUNT), badge)
                }
            }
        }
        return GeneratedVoicings(tuning, entries, key)
    }

    private fun countReadable(text: String, tonicPc: Int): ParseCount {
        val tokens = tokenizeProgression(text)
        return ParseCount(tokens.size, tokens.count { resolveToken(it, tonicPc) is ChordParseResult.Ok })
    }

    // The key finder reads roman numerals in C.
    private fun analyseKey(text: String): KeyAnalysis {
        val tokens = tokenizeProgression(text)
        val chords = tokens.mapNotNull { (resolveToken(it, 0) as? ChordParseResult.Ok)?.chord }
        return KeyAnalysis(ParseCount(tokens.size, chords.size), if (chords.isNotEmpty()) detectKey(chords) else null)
    }

    private fun parsedTuningOf(tuning: Tuning): ParsedTuning? {
        val tokens = tuning.strings.map { string ->
            val midi = frequencyToMidiNote(string.freq) ?: return null
            midiName(midi, flats = false)
        }
        return (parseTuning(tokens.joinToString(" ")) as? TuningParseResult.Ok)?.tuning
    }
}

/**
 * A chord symbol, or failing that a roman numeral ("vi", "bVII7") read in the key of [tonicPc].
 * Numerals work quietly: no label or hint mentions them.
 */
internal fun resolveToken(token: String, tonicPc: Int): ChordParseResult {
    val direct = parseChord(token)
    if (direct is ChordParseResult.Ok) return direct
    // A flat numeral ("bVII") spells its root with a flat even in a sharp key: Bb, not A#.
    val flats = flatsForPc(tonicPc) || token.trim().startsWith("b")
    val symbol = degreeToChordSymbol(token, tonicPc, flats) ?: return direct
    return parseChord(symbol) as? ChordParseResult.Ok ?: direct
}

/** "min · B D F#", optional tones in parentheses. */
internal fun chordTonesLabel(chord: ParsedChord, flats: Boolean): String {
    val optional = chord.optionalPcs.toSet()
    val tones = chord.pcs.joinToString(" ") { pc ->
        val name = pcName(pc, flats)
        if (pc in optional) "($name)" else name
    }
    // The root is already in the card title, so only the quality leads the tones.
    return "${chord.quality} · $tones"
}

internal data class TabRow(val label: String, val padLeft: Int, val symbol: String, val padRight: Int, val muted: Boolean)

/** One text-tab line per string, highest string first, the fret centred in a fixed-width cell. */
internal fun tabRows(shape: VoicingShape, tuning: ParsedTuning): List<TabRow> {
    val width = if (shape.frets.any { it != null && it >= 10 }) 8 else 7
    return (tuning.midi.indices.reversed()).map { s ->
        val fret = shape.frets[s]
        val symbol = fret?.toString() ?: "x"
        val padLeft = (width - symbol.length) / 2
        TabRow(tuning.labels[s].padEnd(5), padLeft, symbol, width - symbol.length - padLeft, fret == null)
    }
}
