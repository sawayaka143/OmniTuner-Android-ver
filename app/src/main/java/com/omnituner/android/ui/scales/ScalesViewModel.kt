package com.omnituner.android.ui.scales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnituner.android.audio.GuitarSamplePlayer
import com.omnituner.android.audio.NotePlayer
import com.omnituner.core.audio.midiNoteToFrequency
import com.omnituner.core.data.SCALES
import com.omnituner.core.prefs.InstrumentRegistry
import com.omnituner.core.prefs.ScalePreferences
import com.omnituner.core.prefs.ScalePreferencesState
import com.omnituner.core.theory.FretCell
import com.omnituner.core.theory.computeFretboard
import com.omnituner.core.theory.noteName
import com.omnituner.core.theory.parseNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScalesUiState(
    val prefs: ScalePreferencesState = ScalePreferencesState(),
    val instruments: List<com.omnituner.core.data.Instrument> = emptyList(),
    val tunings: List<com.omnituner.core.data.Tuning> = emptyList(),
    val instrumentLabel: String = "",
    val tuningLabel: String = "",
    val tuningSummary: String = "",
    val tuningFrequencies: List<Double> = emptyList(),
    val board: List<List<FretCell>> = emptyList(),
    val isPlaying: Boolean = false,
)

class ScalesViewModel(
    private val prefs: ScalePreferences,
    private val registry: InstrumentRegistry,
    private val notePlayer: NotePlayer,
    private val samplePlayer: GuitarSamplePlayer?,
) : ViewModel() {

    private val _ui = MutableStateFlow(ScalesUiState())
    val ui: StateFlow<ScalesUiState> = _ui.asStateFlow()

    private var playJob: Job? = null

    init {
        rebuild()
    }

    fun setScaleId(scaleId: String) {
        prefs.setScaleId(scaleId)
        rebuild()
    }

    fun setRootPitchClass(root: Int) {
        prefs.setRootPitchClass(root)
        rebuild()
    }

    fun setAccidental(accidental: String) {
        prefs.setAccidental(accidental)
        rebuild()
    }

    fun setFretCount(fretCount: Int) {
        prefs.setFretCount(fretCount)
        rebuild()
    }

    fun setLabelMode(labelMode: String) {
        prefs.setLabelMode(labelMode)
        rebuild()
    }

    fun setShowOutsideScale(show: Boolean) {
        prefs.setShowOutsideScale(show)
        rebuild()
    }

    fun selectInstrument(instrumentId: String) {
        registry.selectInstrument(instrumentId)
        rebuild()
    }

    fun selectTuning(tuningId: String) {
        registry.selectTuning(tuningId)
        rebuild()
    }

    fun playCell(cell: FretCell) {
        val midi = cell.midi ?: return
        if (samplePlayer != null && samplePlayer.playSampleNote(midi)) return
        notePlayer.playNote(midi)
    }

    fun playScale(down: Boolean = false) {
        if (_ui.value.isPlaying) {
            playJob?.cancel()
            playJob = null
            _ui.value = _ui.value.copy(isPlaying = false)
            return
        }
        playJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isPlaying = true)
            val scaleCells = _ui.value.board
                .flatten()
                .filter { it.interval != null && it.midi != null }
                .sortedBy { it.midi }
            val sequence = if (down) scaleCells.asReversed() else scaleCells
            for (cell in sequence) {
                if (!_ui.value.isPlaying) break
                playCell(cell)
                delay(330)
            }
            _ui.value = _ui.value.copy(isPlaying = false)
        }
    }

    fun rebuild() {
        val prefsState = prefs.stateFlow.value
        val tuning = registry.selectedTuning()
        val openMidi = tuning.strings.map { string ->
            parseNote(string.name)?.let { pc -> pc } ?: run {
                // derive pitch class from frequency when the label is unusual
                val midi = com.omnituner.core.audio.frequencyToMidiNote(string.freq) ?: 69
                ((midi % 12) + 12) % 12
            }
        }
        val midiNotes = tuning.strings.map { com.omnituner.core.audio.frequencyToMidiNote(it.freq) ?: 69 }
        val scale = SCALES.find { it.id == prefsState.scaleId } ?: SCALES.first()
        val board = computeFretboard(
            openPitchClasses = openMidi,
            fretCount = prefsState.fretCount,
            intervals = scale.intervals,
            preferFlats = prefsState.accidental == "flat",
            openMidiNotes = midiNotes,
        )

        _ui.value = ScalesUiState(
            prefs = prefsState,
            instruments = registry.instruments(),
            tunings = registry.availableTunings(),
            instrumentLabel = registry.selectedInstrument().label,
            tuningLabel = tuning.label,
            tuningSummary = tuning.strings.joinToString(" ") { it.name },
            tuningFrequencies = tuning.strings.map { it.freq },
            board = board,
            isPlaying = _ui.value.isPlaying,
        )
    }

    fun previewTuningNote(index: Int) {
        val freq = _ui.value.tuningFrequencies.getOrNull(index) ?: return
        val midi = com.omnituner.core.audio.frequencyToMidiNote(freq) ?: return
        if (samplePlayer != null && samplePlayer.playSampleNote(midi)) return
        notePlayer.playNote(midi)
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(
            prefs: ScalePreferences,
            registry: InstrumentRegistry,
            notePlayer: NotePlayer,
            samplePlayer: GuitarSamplePlayer?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScalesViewModel(prefs, registry, notePlayer, samplePlayer) }
        }
    }
}
