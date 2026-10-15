package com.omnituner.android.ui.chords

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.data.LibraryState
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.data.ProgressionPreset
import com.omnituner.core.data.Tuning
import com.omnituner.core.theory.BadgeKind
import com.omnituner.core.theory.ChordParseResult
import com.omnituner.core.theory.DetectedKey
import com.omnituner.core.theory.DiatonicBadge
import com.omnituner.core.theory.MODE_NAMES
import com.omnituner.core.theory.ParsedChord
import com.omnituner.core.theory.ParsedTuning
import com.omnituner.core.theory.TuningParseResult
import com.omnituner.core.theory.VoicingShape
import com.omnituner.core.theory.computeBadgeForPc
import com.omnituner.core.theory.degreeToChordSymbol
import com.omnituner.core.theory.detectKey
import com.omnituner.core.theory.midiName
import com.omnituner.core.theory.parseChord
import com.omnituner.core.theory.parseTuning
import com.omnituner.core.theory.searchChord
import com.omnituner.core.theory.tokenizeProgression
import com.omnituner.core.theory.tonicPcOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHORD_DURATION_SECONDS = 1.2
private const val PROGRESSION_GAP_MS = 700L
private const val VOICING_NOTE_GAP_MS = 90L

data class ChordFinderUiState(
    val chordInput: String = "C",
    val parsedChord: ParsedChord? = null,
    val chordError: String? = null,
    val badge: DiatonicBadge? = null,
    val progressionInput: String = "C G Am F",
    val progression: List<ParsedChord> = emptyList(),
    val detectedKey: DetectedKey? = null,
    val tonic: String = "C",
    val voicings: List<VoicingShape> = emptyList(),
    val tuning: Tuning? = null,
)

private data class ChordInputs(
    val chord: String = "C",
    val progression: String = "C G Am F",
    val tonic: String = "C",
)

class ChordFinderViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val library = container.instruments
    private val noteAudio = container.noteAudio
    private val inputs = MutableStateFlow(ChordInputs())
    private var playJob: Job? = null

    val ui: StateFlow<ChordFinderUiState> = combine(inputs, library.state) { current, libraryState ->
        compute(current, libraryState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChordFinderUiState())

    fun setChordInput(chord: String) {
        inputs.update { it.copy(chord = chord) }
    }

    fun setProgressionInput(progression: String) {
        inputs.update { it.copy(progression = progression) }
    }

    fun setTonic(tonic: String) {
        if (tonicPcOf(tonic) == null) return
        inputs.update { it.copy(tonic = tonic) }
    }

    fun applyPreset(preset: ProgressionPreset) {
        val tonicPc = tonicPcOf(inputs.value.tonic) ?: 0
        val symbols = preset.degrees.mapNotNull { degreeToChordSymbol(it, tonicPc, useFlats = false) }
        inputs.update { it.copy(progression = symbols.joinToString(" ")) }
    }

    fun playChord(chord: ParsedChord) {
        val tuning = parsedTuningOf(library.state.value.tuning) ?: return
        val voicing = searchChord(tuning, chord).firstOrNull() ?: return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            for (note in voicing.sounding.asReversed()) {
                noteAudio.playNote(note.midi)
                delay(VOICING_NOTE_GAP_MS)
            }
        }
    }

    fun playProgression() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val tuning = parsedTuningOf(library.state.value.tuning) ?: return@launch
            for (chord in ui.value.progression) {
                val voicing = searchChord(tuning, chord).firstOrNull() ?: continue
                for (note in voicing.sounding) {
                    noteAudio.playNote(note.midi, durationSeconds = CHORD_DURATION_SECONDS)
                }
                delay(PROGRESSION_GAP_MS)
            }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private fun compute(current: ChordInputs, libraryState: LibraryState): ChordFinderUiState {
        val chordResult = parseChord(current.chord)
        val parsed = (chordResult as? ChordParseResult.Ok)?.chord
        val progression = tokenizeProgression(current.progression)
            .mapNotNull { (parseChord(it) as? ChordParseResult.Ok)?.chord }
        val tuning = libraryState.tuning
        val parsedTuning = parsedTuningOf(tuning)
        val tonicPc = tonicPcOf(current.tonic) ?: 0

        return ChordFinderUiState(
            chordInput = current.chord,
            parsedChord = parsed,
            chordError = (chordResult as? ChordParseResult.Error)?.error,
            badge = parsed?.let { badgeFor(it, tonicPc) },
            progressionInput = current.progression,
            progression = progression,
            detectedKey = detectKey(progression),
            tonic = current.tonic,
            voicings = if (parsed != null && parsedTuning != null) searchChord(parsedTuning, parsed) else emptyList(),
            tuning = tuning,
        )
    }

    private fun badgeFor(chord: ParsedChord, tonicPc: Int): DiatonicBadge? {
        var first: DiatonicBadge? = null
        for (mode in MODE_NAMES) {
            val candidate = computeBadgeForPc(chord, tonicPc, mode, tuningFlats = false, tonicFlats = false)
            if (candidate.kind == BadgeKind.Good) return candidate
            if (first == null) first = candidate
        }
        return first
    }

    private fun parsedTuningOf(tuning: Tuning): ParsedTuning? {
        val tokens = tuning.strings.map { string ->
            val midi = frequencyToMidiNote(string.freq) ?: return null
            midiName(midi, flats = false)
        }
        return (parseTuning(tokens.joinToString(" ")) as? TuningParseResult.Ok)?.tuning
    }
}
