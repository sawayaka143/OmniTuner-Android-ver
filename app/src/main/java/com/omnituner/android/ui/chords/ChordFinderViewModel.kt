package com.omnituner.android.ui.chords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnituner.android.audio.GuitarSamplePlayer
import com.omnituner.android.audio.NotePlayer
import com.omnituner.core.data.PROGRESSION_PRESETS
import com.omnituner.core.data.ProgressionPreset
import com.omnituner.core.prefs.InstrumentRegistry
import com.omnituner.core.prefs.ScalePreferences
import com.omnituner.core.theory.ChordParseResult
import com.omnituner.core.theory.DetectedKey
import com.omnituner.core.theory.DiatonicBadge
import com.omnituner.core.theory.ParsedChord
import com.omnituner.core.theory.TuningParseResult
import com.omnituner.core.theory.VoicingShape
import com.omnituner.core.theory.computeBadgeForPc
import com.omnituner.core.theory.degreeToChordSymbol
import com.omnituner.core.theory.detectKey
import com.omnituner.core.theory.mod12
import com.omnituner.core.theory.parseChord
import com.omnituner.core.theory.parseTuning
import com.omnituner.core.theory.searchChord
import com.omnituner.core.theory.tonicPcOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChordFinderUiState(
    val chordInput: String = "C",
    val parsedChord: ParsedChord? = null,
    val chordError: String? = null,
    val progressionInput: String = "C G Am F",
    val progression: List<ParsedChord> = emptyList(),
    val detectedKey: DetectedKey? = null,
    val tonicInput: String = "C",
    val tonicPc: Int = 0,
    val badges: Map<String, DiatonicBadge> = emptyMap(),
    val voicings: List<VoicingShape> = emptyList(),
    val tuningParseError: String? = null,
    val tuningSummary: String = "",
    val useFlats: Boolean = false,
)

class ChordFinderViewModel(
    private val scalePrefs: ScalePreferences,
    private val registry: InstrumentRegistry,
    private val notePlayer: NotePlayer,
    private val samplePlayer: GuitarSamplePlayer?,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChordFinderUiState())
    val ui: StateFlow<ChordFinderUiState> = _ui.asStateFlow()

    init {
        rebuild()
    }

    fun setChordInput(input: String) {
        _ui.value = _ui.value.copy(chordInput = input)
        rebuild()
    }

    fun setProgressionInput(input: String) {
        _ui.value = _ui.value.copy(progressionInput = input)
        rebuild()
    }

    fun setTonic(input: String) {
        val pc = tonicPcOf(input) ?: return
        _ui.value = _ui.value.copy(tonicInput = input, tonicPc = pc)
        rebuild()
    }

    fun applyPreset(preset: ProgressionPreset) {
        val symbols = preset.degrees
            .mapNotNull { degreeToChordSymbol(it, _ui.value.tonicPc, _ui.value.useFlats) }
        _ui.value = _ui.value.copy(progressionInput = symbols.joinToString(" "))
        rebuild()
    }

    fun playChord(chord: ParsedChord) {
        val tuning = currentParsedTuning() ?: return
        val voicing = searchChord(tuning, chord).firstOrNull() ?: return
        playVoicing(voicing, tuning)
    }

    fun playVoicing(shape: VoicingShape, tuning: com.omnituner.core.theory.ParsedTuning) {
        viewModelScope.launch {
            for (note in shape.sounding.asReversed()) {
                val midi = tuning.midi[note.stringIndex] + note.fret
                if (samplePlayer != null && samplePlayer.playSampleNote(midi)) {
                    delay(90)
                    continue
                }
                notePlayer.playNote(midi)
                delay(90)
            }
        }
    }

    fun playProgression() {
        viewModelScope.launch {
            val tuning = currentParsedTuning() ?: return@launch
            for (chord in _ui.value.progression) {
                val voicing = searchChord(tuning, chord).firstOrNull() ?: continue
                val notes = voicing.sounding.map {
                    tuning.midi[it.stringIndex] + it.fret
                }
                for (midi in notes) {
                    if (samplePlayer != null && samplePlayer.playSampleNote(midi, durationSeconds = 1.2)) {
                        continue
                    }
                    notePlayer.playNote(midi, durationSeconds = 1.2)
                }
                delay(700)
            }
        }
    }

    fun rebuild() {
        val chordResult = parseChord(_ui.value.chordInput)
        val parsedChord = (chordResult as? ChordParseResult.Ok)?.chord

        val tokens = com.omnituner.core.theory.tokenizeProgression(_ui.value.progressionInput)
        val progression = tokens.mapNotNull { (parseChord(it) as? ChordParseResult.Ok)?.chord }
        val detectedKey = detectKey(progression)

        val tonicPc = _ui.value.tonicPc
        val badges = if (parsedChord != null) {
            var badge: DiatonicBadge? = null
            for (mode in com.omnituner.core.theory.MODE_NAMES) {
                val candidate = computeBadgeForPc(parsedChord, tonicPc, mode, _ui.value.useFlats, _ui.value.useFlats)
                if (candidate?.kind == "good") {
                    badge = candidate
                    break
                }
                if (badge == null) badge = candidate
            }
            parsedChord.symbol.let { symbol -> mapOf(symbol to (badge ?: return@let emptyMap())) }
        } else {
            emptyMap()
        }

        val tuning = currentParsedTuning()
        val voicings = if (parsedChord != null && tuning != null) {
            searchChord(tuning, parsedChord)
        } else {
            emptyList()
        }

        val tuningSummary = registry.selectedTuning().strings.joinToString(" ") { it.name }

        _ui.value = _ui.value.copy(
            parsedChord = parsedChord,
            chordError = (chordResult as? ChordParseResult.Error)?.error,
            progression = progression,
            detectedKey = detectedKey,
            badges = badges,
            voicings = voicings,
            tuningParseError = null,
            tuningSummary = tuningSummary,
        )
    }

    private fun currentParsedTuning(): com.omnituner.core.theory.ParsedTuning? {
        val tuning = registry.selectedTuning()
        val tokens = tuning.strings.map { string ->
            val midi = com.omnituner.core.audio.frequencyToMidiNote(string.freq) ?: return null
            com.omnituner.core.theory.midiName(midi, flats = _ui.value.useFlats)
        }
        return when (val result = parseTuning(tokens.joinToString(" "))) {
            is TuningParseResult.Ok -> result.tuning
            is TuningParseResult.Error -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun factory(
            scalePrefs: ScalePreferences,
            registry: InstrumentRegistry,
            notePlayer: NotePlayer,
            samplePlayer: GuitarSamplePlayer?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChordFinderViewModel(scalePrefs, registry, notePlayer, samplePlayer)
            }
        }
    }
}
