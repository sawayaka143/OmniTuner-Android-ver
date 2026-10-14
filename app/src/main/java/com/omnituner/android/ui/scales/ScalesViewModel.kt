package com.omnituner.android.ui.scales

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.data.Accidental
import com.omnituner.android.data.InstrumentLibrary
import com.omnituner.android.data.LabelMode
import com.omnituner.android.data.LibraryState
import com.omnituner.android.data.ScaleSettings
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.data.INSTRUMENTS
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.SCALES
import com.omnituner.core.data.Tuning
import com.omnituner.core.theory.FretCell
import com.omnituner.core.theory.computeFretboard
import com.omnituner.core.theory.mod12
import com.omnituner.core.theory.parseNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SCALE_NOTE_DELAY_MS = 330L

data class ScalesUiState(
    val settings: ScaleSettings = ScaleSettings(),
    val instruments: List<Instrument> = emptyList(),
    val instrument: Instrument = INSTRUMENTS.first(),
    val tunings: List<Tuning> = emptyList(),
    val tuning: Tuning = INSTRUMENTS.first().tunings.first(),
    val board: List<List<FretCell>> = emptyList(),
    val isPlaying: Boolean = false,
)

class ScalesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val settingsStore = container.scales
    private val library: InstrumentLibrary = container.instruments
    private val noteAudio = container.noteAudio
    private val playing = MutableStateFlow(false)
    private var playJob: Job? = null

    val ui: StateFlow<ScalesUiState> = combine(
        settingsStore.flow,
        library.state,
        playing,
    ) { settings, libraryState, isPlaying -> buildState(settings, libraryState, isPlaying) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScalesUiState())

    fun setScaleId(scaleId: String) = edit { it.copy(scaleId = scaleId) }

    fun setRootPitchClass(root: Int) = edit { it.copy(rootPitchClass = root) }

    fun setAccidental(accidental: Accidental) = edit { it.copy(accidental = accidental) }

    fun setFretCount(fretCount: Int) = edit { it.copy(fretCount = fretCount) }

    fun setLabelMode(labelMode: LabelMode) = edit { it.copy(labelMode = labelMode) }

    fun setShowOutsideScale(show: Boolean) = edit { it.copy(showOutsideScale = show) }

    fun selectInstrument(instrumentId: String) {
        viewModelScope.launch { library.selectInstrument(instrumentId) }
    }

    fun selectTuning(tuningId: String) {
        viewModelScope.launch { library.selectTuning(tuningId) }
    }

    fun playCell(cell: FretCell) {
        val midi = cell.midi ?: return
        noteAudio.playNote(midi)
    }

    fun togglePlayScale() {
        if (playing.value) {
            playJob?.cancel()
            playJob = null
            playing.value = false
            return
        }
        playJob = viewModelScope.launch {
            playing.value = true
            val cells = ui.value.board
                .flatten()
                .filter { it.interval != null && it.midi != null }
                .sortedBy { it.midi }
            for (cell in cells) {
                if (!playing.value) break
                playCell(cell)
                delay(SCALE_NOTE_DELAY_MS)
            }
            playing.value = false
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private fun edit(transform: (ScaleSettings) -> ScaleSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    private fun buildState(settings: ScaleSettings, libraryState: LibraryState, isPlaying: Boolean): ScalesUiState {
        return ScalesUiState(
            settings = settings,
            instruments = libraryState.instruments,
            instrument = libraryState.instrument,
            tunings = libraryState.tunings,
            tuning = libraryState.tuning,
            board = computeBoard(settings, libraryState.tuning),
            isPlaying = isPlaying,
        )
    }

    private fun computeBoard(settings: ScaleSettings, tuning: Tuning): List<List<FretCell>> {
        val midiNotes = tuning.strings.map { frequencyToMidiNote(it.freq) ?: 69 }
        val pitchClasses = tuning.strings.mapIndexed { index, string ->
            parseNote(string.name) ?: mod12(midiNotes[index])
        }
        val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()
        return computeFretboard(
            openPitchClasses = pitchClasses.reversed(),
            fretCount = settings.fretCount,
            intervals = scale.intervals,
            preferFlats = settings.accidental == Accidental.Flat,
            openMidiNotes = midiNotes.reversed(),
        )
    }
}
