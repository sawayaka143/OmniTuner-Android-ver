package com.omnituner.android.ui.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.audio.PitchCapture
import com.omnituner.android.audio.PitchFrame
import com.omnituner.android.data.IN_TUNE_HOLD_MS
import com.omnituner.android.data.IN_TUNE_TOLERANCE_CENTS
import com.omnituner.android.data.InstrumentTunings
import com.omnituner.android.data.LibraryState
import com.omnituner.android.data.RecentTuning
import com.omnituner.android.data.TunerMode
import com.omnituner.android.data.TunerSettings
import com.omnituner.core.audio.PitchTrackingState
import com.omnituner.core.audio.centsFromMidiFloat
import com.omnituner.core.audio.frequencyToMidiFloat
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.audio.nearestSemitone
import com.omnituner.core.audio.nearestStringTarget
import com.omnituner.core.audio.needlePercentFromCents
import com.omnituner.core.data.INSTRUMENTS
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.InvalidInstrumentException
import com.omnituner.core.data.NamedFrequency
import com.omnituner.core.data.Tuning
import com.omnituner.core.data.validateInstrumentName
import com.omnituner.core.data.validateNotes
import com.omnituner.core.data.validateStringCount
import com.omnituner.core.data.validateTuningName
import kotlin.math.abs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val LOCK_PULSE_MS = 900L
private const val NOTE_SNAP_CENTS = 50.0

sealed interface TuneStatus {
    data object Idle : TuneStatus
    data object Listening : TuneStatus
    data class Adjusting(val stringName: String) : TuneStatus
    data object InTune : TuneStatus
}

data class TunerUiState(
    val capturing: Boolean = false,
    val error: String? = null,
    val mode: TunerMode = TunerMode.Auto,
    val instruments: List<Instrument> = emptyList(),
    val instrument: Instrument = INSTRUMENTS.first(),
    val tunings: List<Tuning> = emptyList(),
    val tuning: Tuning = INSTRUMENTS.first().tunings.first(),
    val catalog: List<InstrumentTunings> = emptyList(),
    val activeIndex: Int? = null,
    val cents: Double? = null,
    val tolerance: Int = 5,
    val needlePercent: Float = 50f,
    val noteMidi: Int? = null,
    val frequency: Double? = null,
    val tuned: Boolean = false,
    val status: TuneStatus = TuneStatus.Idle,
    val tunedStrings: Set<String> = emptySet(),
    val pulse: Boolean = false,
    val haptics: Boolean = true,
    val proAccuracy: Boolean = false,
    val recent: List<RecentTuning> = emptyList(),
)

private data class LockSnapshot(
    val confirmed: Boolean = false,
    val tunedStrings: Set<String> = emptySet(),
    val pulseUntil: Long = 0L,
)

private data class Session(
    val frame: PitchFrame,
    val capturing: Boolean,
    val error: String?,
    val lock: LockSnapshot,
)

private data class Controls(
    val mode: TunerMode?,
    val manualIndex: Int,
    val settings: TunerSettings,
    val library: LibraryState,
)

private data class Reading(
    val activeIndex: Int?,
    val cents: Double?,
    val noteMidi: Int?,
    val targetName: String?,
)

private fun readFrame(
    frame: PitchFrame,
    mode: TunerMode,
    manualIndex: Int,
    strings: List<NamedFrequency>,
    referencePitch: Int,
    previousTarget: String?,
    capturing: Boolean,
): Reading {
    val played = frame.frequency?.let { frequencyToMidiFloat(it, referencePitch.toDouble()) }

    if (mode == TunerMode.Manual) {
        val index = if (strings.isEmpty()) null else minOf(manualIndex, strings.lastIndex)
        val targetMidi = index?.let { frequencyToMidiNote(strings[it].freq) ?: 69 }
        val cents = if (played != null && targetMidi != null) centsFromMidiFloat(played, targetMidi) else null
        val noteMidi = if (targetMidi != null && cents != null && abs(cents) < NOTE_SNAP_CENTS) {
            targetMidi
        } else {
            nearestSemitone(played)
        }
        return Reading(index, cents, noteMidi, index?.let { strings[it].name })
    }

    val target = if (capturing && played != null) nearestStringTarget(played, strings, previousTarget) else null
    val cents = target?.cents
    val noteMidi = if (target != null && cents != null && abs(cents) < NOTE_SNAP_CENTS) {
        target.midi
    } else {
        nearestSemitone(played)
    }
    val index = target?.let { found -> strings.indexOfFirst { it.name == found.name }.takeIf { it >= 0 } }
    return Reading(index, cents, noteMidi, target?.name)
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val settingsStore = container.tuner
    private val library = container.instruments
    private val noteAudio = container.noteAudio
    private val capture = PitchCapture(application)

    private val frameEvents = MutableSharedFlow<PitchFrame>(
        extraBufferCapacity = FRAME_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val latestFrame = MutableStateFlow(PitchFrame.Silent)
    private val capturing = MutableStateFlow(false)
    private val captureError = MutableStateFlow<String?>(null)
    private val mode = MutableStateFlow<TunerMode?>(null)
    private val manualIndex = MutableStateFlow(0)
    private val lockState = MutableStateFlow(LockSnapshot())
    private val lock = TuneLock()
    private val lockChannel = Channel<Unit>(Channel.BUFFERED)
    private var previousTarget: String? = null

    val lockEvents: Flow<Unit> = lockChannel.receiveAsFlow()

    private val session: StateFlow<Session> = combine(
        latestFrame,
        capturing,
        captureError,
        lockState,
    ) { frame, active, error, snapshot -> Session(frame, active, error, snapshot) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session(PitchFrame.Silent, false, null, LockSnapshot()))

    private val controls: StateFlow<Controls> = combine(
        mode,
        manualIndex,
        settingsStore.flow,
        library.state,
    ) { currentMode, index, settings, libraryState -> Controls(currentMode, index, settings, libraryState) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Controls(null, 0, TunerSettings(), library.state.value),
        )

    val ui: StateFlow<TunerUiState> = combine(session, controls) { current, state -> buildState(current, state) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TunerUiState())

    init {
        viewModelScope.launch {
            mode.value = settingsStore.current().initialMode()
        }
        viewModelScope.launch {
            frameEvents.collect { onFrame(it) }
        }
    }

    fun startCapture() {
        if (capturing.value) return
        val error = capture.start { frame ->
            latestFrame.value = frame
            frameEvents.tryEmit(frame)
        }
        capturing.value = error == null
        captureError.value = error
    }

    fun stopCapture() {
        if (!capturing.value) return
        capture.stop()
        capturing.value = false
        latestFrame.value = PitchFrame.Silent
        frameEvents.tryEmit(PitchFrame.Silent)
    }

    fun onPermissionDenied() {
        stopCapture()
        captureError.value = "Microphone access is unavailable. Check permissions and try again."
    }

    fun selectMode(newMode: TunerMode) {
        val current = mode.value ?: return
        if (current == newMode) return
        if (newMode == TunerMode.Manual) {
            val reading = readFrame(
                latestFrame.value,
                TunerMode.Auto,
                manualIndex.value,
                library.state.value.tuning.strings,
                settingsStore.flow.value.referencePitch,
                previousTarget,
                capturing.value,
            )
            reading.activeIndex?.let { manualIndex.value = it }
        }
        mode.value = newMode
        resetHold()
        viewModelScope.launch { settingsStore.update { it.copy(mode = newMode) } }
    }

    fun selectString(index: Int) {
        manualIndex.value = index
        mode.value = TunerMode.Manual
        resetHold()
        viewModelScope.launch { settingsStore.update { it.copy(mode = TunerMode.Manual) } }
        library.state.value.tuning.strings.getOrNull(index)?.let { string ->
            noteAudio.playNote(frequencyToMidiNote(string.freq) ?: 69)
        }
    }

    fun selectTuning(instrumentId: String, tuningId: String) {
        val current = library.state.value
        if (current.instrument.id == instrumentId && current.tuning.id == tuningId) return
        resetHold()
        viewModelScope.launch { library.select(instrumentId, tuningId) }
    }

    fun saveCustomTuning(editingId: String?, name: String, notes: List<Int>): String? {
        val instrument = library.state.value.instrument
        return try {
            val validName = validateTuningName(name)
            val validNotes = validateNotes(notes, instrument.stringCount)
            viewModelScope.launch { library.saveTuning(editingId, instrument.id, validName, validNotes) }
            resetHold()
            null
        } catch (e: InvalidInstrumentException) {
            e.message
        }
    }

    fun deleteCustomTuning(tuningId: String) {
        resetHold()
        viewModelScope.launch { library.deleteTuning(tuningId) }
    }

    fun saveCustomInstrument(editingId: String?, name: String, stringCount: Int, notes: List<Int>): String? {
        return try {
            val validName = validateInstrumentName(name)
            val validCount = validateStringCount(stringCount)
            val validNotes = validateNotes(notes, validCount)
            viewModelScope.launch { library.saveInstrument(editingId, validName, validCount, validNotes) }
            resetHold()
            null
        } catch (e: InvalidInstrumentException) {
            e.message
        }
    }

    fun deleteCustomInstrument(instrumentId: String) {
        resetHold()
        viewModelScope.launch { library.deleteInstrument(instrumentId) }
    }

    private fun onFrame(frame: PitchFrame) {
        val currentMode = mode.value ?: return
        val settings = settingsStore.flow.value
        val reading = readFrame(
            frame,
            currentMode,
            manualIndex.value,
            library.state.value.tuning.strings,
            settings.referencePitch,
            previousTarget,
            capturing.value,
        )
        if (currentMode == TunerMode.Auto && frame.tracking == PitchTrackingState.LOCKED) {
            reading.targetName?.let { previousTarget = it }
        }

        val now = SystemClock.elapsedRealtime()
        val justConfirmed = lock.step(
            now = now,
            cents = reading.cents,
            tracking = frame.tracking,
            tolerance = IN_TUNE_TOLERANCE_CENTS.toDouble(),
            holdMs = IN_TUNE_HOLD_MS,
        )
        if (justConfirmed) onConfirmed(reading, settings, currentMode, now)
        lockState.update { it.copy(confirmed = lock.confirmed) }
    }

    private fun onConfirmed(reading: Reading, settings: TunerSettings, currentMode: TunerMode, now: Long) {
        lockChannel.trySend(Unit)
        val name = reading.targetName
        lockState.update { snapshot ->
            val tunedStrings = if (currentMode == TunerMode.Auto && name != null) {
                snapshot.tunedStrings + name
            } else {
                snapshot.tunedStrings
            }
            val pulseUntil = if (settings.inTuneFeedback) now + LOCK_PULSE_MS else snapshot.pulseUntil
            snapshot.copy(tunedStrings = tunedStrings, pulseUntil = pulseUntil)
        }
        if (settings.inTuneFeedback && settings.chime) noteAudio.playChime()
    }

    private fun buildState(current: Session, state: Controls): TunerUiState {
        val settings = state.settings
        val currentMode = state.mode ?: TunerMode.Auto
        val reading = readFrame(
            current.frame,
            currentMode,
            state.manualIndex,
            state.library.tuning.strings,
            settings.referencePitch,
            previousTarget,
            current.capturing,
        )
        val tracking = current.frame.tracking
        val cents = reading.cents
        val withinTolerance = cents != null && abs(cents) <= IN_TUNE_TOLERANCE_CENTS
        val showTuned = settings.inTuneFeedback && current.lock.confirmed
        val tunedNow = if (settings.inTuneFeedback) showTuned else tracking == PitchTrackingState.LOCKED && withinTolerance
        val targetName = reading.targetName

        val status = when {
            !current.capturing -> TuneStatus.Idle
            showTuned -> TuneStatus.InTune
            targetName != null && tracking == PitchTrackingState.LOCKED -> TuneStatus.Adjusting(targetName)
            else -> TuneStatus.Listening
        }

        return TunerUiState(
            capturing = current.capturing,
            error = current.error,
            mode = currentMode,
            instruments = state.library.instruments,
            instrument = state.library.instrument,
            tunings = state.library.tunings,
            tuning = state.library.tuning,
            catalog = state.library.catalog,
            activeIndex = reading.activeIndex,
            cents = cents,
            tolerance = IN_TUNE_TOLERANCE_CENTS,
            needlePercent = needlePercentFromCents(cents).toFloat(),
            noteMidi = reading.noteMidi,
            frequency = current.frame.frequency,
            tuned = tunedNow && reading.activeIndex != null,
            status = status,
            tunedStrings = current.lock.tunedStrings,
            pulse = current.lock.pulseUntil > SystemClock.elapsedRealtime(),
            haptics = settings.inTuneFeedback && settings.haptics,
            proAccuracy = settings.proAccuracy,
            recent = state.library.recent,
        )
    }

    private fun resetHold() {
        lock.reset()
        previousTarget = null
        lockState.value = LockSnapshot()
    }

    override fun onCleared() {
        capture.stop()
    }

    private companion object {
        const val FRAME_BUFFER = 8
    }
}
