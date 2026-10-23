package com.omnituner.android.ui.scales

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.data.Accidental
import com.omnituner.android.data.InstrumentLibrary
import com.omnituner.android.data.InstrumentTunings
import com.omnituner.android.data.LabelMode
import com.omnituner.android.data.LibraryState
import com.omnituner.android.data.ScaleSettings
import com.omnituner.android.ui.common.SoundingNote
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.data.INSTRUMENTS
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.SCALES
import com.omnituner.core.data.Tuning
import com.omnituner.core.theory.FretCell
import com.omnituner.core.theory.computeFretboard
import com.omnituner.core.theory.mod12
import com.omnituner.core.theory.noteName
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
private const val TAP_PULSE_MS = 240L

/** One note of the selected scale, voiced in the octave above the lowest open string. */
data class ScaleTone(
    val pitchClass: Int,
    val midi: Int,
    val noteName: String,
    val intervalLabel: String,
    val isRoot: Boolean,
)

data class ScalesUiState(
    val settings: ScaleSettings = ScaleSettings(),
    val instrument: Instrument = INSTRUMENTS.first(),
    val tuning: Tuning = INSTRUMENTS.first().tunings.first(),
    /** Every instrument with its tunings, for the tuning sheet. */
    val catalog: List<InstrumentTunings> = emptyList(),
    val board: List<List<FretCell>> = emptyList(),
    val tones: List<ScaleTone> = emptyList(),
    val isPlaying: Boolean = false,
    val sounding: SoundingNote? = null,
)

class ScalesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val settingsStore = container.scales
    private val library: InstrumentLibrary = container.instruments
    private val noteAudio = container.noteAudio
    private val playing = MutableStateFlow(false)
    private val sounding = MutableStateFlow<SoundingNote?>(null)
    private var playJob: Job? = null

    // The board only depends on the settings and tuning, so a sounding note doesn't rebuild it.
    private val base = combine(settingsStore.flow, library.state, ::buildState)

    val ui: StateFlow<ScalesUiState> = combine(base, playing, sounding) { state, isPlaying, note ->
        state.copy(isPlaying = isPlaying, sounding = note)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScalesUiState())

    fun setScaleId(scaleId: String) = edit { it.copy(scaleId = scaleId) }

    fun setRootPitchClass(root: Int) = edit { it.copy(rootPitchClass = root) }

    fun setAccidental(accidental: Accidental) = edit { it.copy(accidental = accidental) }

    fun setFretCount(fretCount: Int) = edit { it.copy(fretCount = fretCount) }

    fun setLabelMode(labelMode: LabelMode) = edit { it.copy(labelMode = labelMode) }

    fun setShowOutsideScale(show: Boolean) = edit { it.copy(showOutsideScale = show) }

    fun selectTuning(instrumentId: String, tuningId: String) {
        viewModelScope.launch { library.select(instrumentId, tuningId) }
    }

    // A tap sounds the note and briefly lights that one position (and its chip in the tone row);
    // while the scale is playing the run owns the highlight.
    fun playCell(cell: FretCell) {
        val midi = cell.midi ?: return
        pulse(SoundingNote(midi, cell.stringIndex, cell.fret))
    }

    // A chip has no position of its own, so it lights the lowest-fret place to play it.
    fun playTone(midi: Int) {
        pulse(noteAt(midi))
    }

    private fun pulse(note: SoundingNote) {
        noteAudio.playNote(note.midi)
        if (playing.value) return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            sounding.value = note
            delay(TAP_PULSE_MS)
            sounding.value = null
        }
    }

    private fun noteAt(midi: Int): SoundingNote {
        val cell = ui.value.board.flatten()
            .filter { it.midi == midi && it.interval != null }
            .minByOrNull { it.fret }
        return SoundingNote(midi, cell?.stringIndex, cell?.fret)
    }

    fun togglePlayScale() {
        if (playing.value) {
            stopPlayback()
            return
        }
        playJob?.cancel()
        // One octave up from the root, then the root an octave higher, like the web app.
        val tones = ui.value.tones
        val run = tones.map { it.midi } + listOfNotNull(tones.firstOrNull()?.midi?.plus(12))
        playJob = viewModelScope.launch {
            playing.value = true
            try {
                for (midi in run) {
                    sounding.value = noteAt(midi)
                    noteAudio.playNote(midi)
                    delay(SCALE_NOTE_DELAY_MS)
                }
            } finally {
                playing.value = false
                sounding.value = null
            }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        playing.value = false
        sounding.value = null
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private fun edit(transform: (ScaleSettings) -> ScaleSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    private fun buildState(settings: ScaleSettings, libraryState: LibraryState): ScalesUiState {
        return ScalesUiState(
            settings = settings,
            instrument = libraryState.instrument,
            tuning = libraryState.tuning,
            catalog = libraryState.catalog,
            board = computeBoard(settings, libraryState.tuning),
            tones = computeTones(settings, libraryState.tuning),
        )
    }

    private fun computeTones(settings: ScaleSettings, tuning: Tuning): List<ScaleTone> {
        val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()
        val flats = settings.accidental == Accidental.Flat
        val lowest = tuning.strings.mapNotNull { frequencyToMidiNote(it.freq) }.minOrNull() ?: 40
        val rootMidi = lowest + mod12(settings.rootPitchClass - lowest)
        return scale.intervals.map { interval ->
            val midi = rootMidi + interval.semitones
            ScaleTone(
                pitchClass = mod12(midi),
                midi = midi,
                noteName = noteName(mod12(midi), flats),
                intervalLabel = interval.label,
                isRoot = mod12(interval.semitones) == 0,
            )
        }
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
            rootPitchClass = settings.rootPitchClass,
        )
    }
}
