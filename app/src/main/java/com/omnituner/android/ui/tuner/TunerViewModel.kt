package com.omnituner.android.ui.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.audio.AudioCaptureEngine
import com.omnituner.android.audio.NotePlayer
import com.omnituner.android.ui.Haptics
import com.omnituner.core.audio.centsFromMidiFloat
import com.omnituner.core.audio.frequencyToMidiFloat
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.audio.hzDisplay
import com.omnituner.core.audio.interpolateColor
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.audio.nearestSemitone
import com.omnituner.core.audio.nearestStringTarget
import com.omnituner.core.audio.needlePercentFromCents
import com.omnituner.core.audio.shouldConfirm
import com.omnituner.core.audio.StringTarget
import com.omnituner.core.audio.tuneCentsText
import com.omnituner.core.audio.tuneColorProgress
import com.omnituner.core.audio.tuneDirectionText
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.NamedFrequency
import com.omnituner.core.data.Tuning
import com.omnituner.core.prefs.InTunePreferences
import com.omnituner.core.prefs.TUNER_MODE_AUTO
import com.omnituner.core.prefs.TUNER_MODE_MANUAL
import com.omnituner.core.prefs.TUNER_STARTUP_REMEMBER
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val LOCK_PULSE_DURATION_MS = 900L
const val RELEASE_HYSTERESIS_MS = 135L

data class TunerUiState(
    val isCapturing: Boolean = false,
    val frequency: Double? = null,
    val trackingState: String = "idle",
    val captureError: String? = null,
    val mode: String = TUNER_MODE_AUTO,
    val instruments: List<Instrument> = emptyList(),
    val tunings: List<Tuning> = emptyList(),
    val strings: List<NamedFrequency> = emptyList(),
    val tuningSummary: String = "",
    val instrumentLabel: String = "",
    val tuningLabel: String = "",
    val selectedInstrumentId: String = "",
    val selectedTuningId: String = "",
    val frameCents: Double? = null,
    val inRange: Boolean = false,
    val confirmed: Boolean = false,
    val pulseActive: Boolean = false,
    val autoTuned: List<String> = emptyList(),
    val activeString: String? = null,
    val needlePercent: Double = 50.0,
    val tunePrompt: String = "—",
    val tuneCents: String = "",
    val tuneColorHex: String? = null,
    val isTuned: Boolean = false,
    val hzText: String = "— Hz",
    val noteName: String? = null,
    val noteOctave: Int? = null,
    val statusMessage: String = "IDLE",
    val manualIndex: Int = 0,
    val inTune: InTunePreferences = InTunePreferences(),
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val prefs = container.tunerPreferences
    private val registry = container.instrumentRegistry
    private val engine = AudioCaptureEngine(application)
    private val player = NotePlayer()
    private val haptics = Haptics(application)

    private data class Analysis(
        val frequency: Double? = null,
        val trackingState: String = "idle",
        val isCapturing: Boolean = false,
    )

    private val analysis = MutableStateFlow(Analysis())

    private val _ui = MutableStateFlow(TunerUiState())
    val ui: StateFlow<TunerUiState> = _ui.asStateFlow()

    // MutableStateFlow-free compose-native state is avoided in VMs; use flows instead.
    private val holdStartedAt = java.util.concurrent.atomic.AtomicLong(0)
    private var holdJob: Job? = null
    private var releaseJob: Job? = null
    private var pulseJob: Job? = null
    private var lastAutoTargetName: String? = null
    private var autoTunedNames: List<String> = emptyList()
    private var confirmed = false
    private var pulseActive = false
    private var mode: String = initialMode()
    private var manualIndex = 0

    private fun initialMode(): String {
        val settings = prefs.tunerSettings.value
        return if (settings.startupMode == TUNER_STARTUP_REMEMBER) settings.mode else settings.startupMode
    }

    init {
        rebuildUi()
    }

    fun uiState(): TunerUiState = _ui.value

    fun toggleCapture() {
        haptics.light()
        if (analysis.value.isCapturing) {
            stopCapture()
        } else {
            startCapture()
        }
    }

    /** Returns null when capture started, or a user-facing error string. */
    fun startCapture(): String? {
        val error = engine.start { frequency, _, trackingState ->
            analysis.value = Analysis(frequency, trackingState.name.lowercase(), true)
            rebuildUi()
        }
        if (error == null) {
            analysis.value = analysis.value.copy(isCapturing = true, trackingState = "listening")
            rebuildUi()
        } else {
            analysis.value = analysis.value.copy(isCapturing = false, trackingState = "idle")
            _ui.value = _ui.value.copy(captureError = error)
            rebuildUi()
        }
        return error
    }

    fun stopCapture() {
        engine.stop()
        analysis.value = Analysis(frequency = null, trackingState = "idle", isCapturing = false)
        rebuildUi()
    }

    fun selectMode(newMode: String) {
        if (mode == newMode) return
        if (newMode == TUNER_MODE_MANUAL) {
            val targetName = currentAutoTarget()?.name
            if (targetName != null) {
                val index = currentStrings().indexOfFirst { it.name == targetName }
                if (index != -1) manualIndex = index
            }
        }
        mode = newMode
        prefs.setMode(newMode)
        resetHoldState()
        rebuildUi()
    }

    fun selectString(index: Int) {
        manualIndex = index
        mode = TUNER_MODE_MANUAL
        prefs.setMode(TUNER_MODE_MANUAL)
        resetHoldState()

        val string = currentStrings().getOrNull(index)
        if (string != null) {
            player.playNote(frequencyToMidiNote(string.freq) ?: 69)
        }
        rebuildUi()
    }

    fun selectInstrument(instrumentId: String) {
        if (registry.selectedInstrumentIdFlow.value == instrumentId) return
        registry.selectInstrument(instrumentId)
        rebuildUi()
    }

    fun selectTuning(tuningId: String) {
        registry.selectTuning(tuningId)
        rebuildUi()
    }

    fun clearError() {
        _ui.value = _ui.value.copy(captureError = null)
    }

    fun onPermissionDenied() {
        analysis.value = analysis.value.copy(isCapturing = false, trackingState = "idle")
        _ui.value = _ui.value.copy(
            captureError = "Microphone access is unavailable. Check permissions and try again.",
        )
        rebuildUi()
    }

    // ---------------------------------------------------------------- internals

    private fun currentStrings(): List<NamedFrequency> =
        registry.selectedTuning().strings

    private fun currentSettings() = prefs.tunerSettings.value

    private fun refPitch(): Double = currentSettings().referencePitch.toDouble()

    private fun tolerance(): Double = currentSettings().inTune.tolerance.toDouble()

    private fun currentAutoTarget(): StringTarget? {
        if (!analysis.value.isCapturing) return null
        val freq = analysis.value.frequency ?: return null
        if (freq <= 0) return null
        val played = frequencyToMidiFloat(freq, refPitch()) ?: return null
        val target = nearestStringTarget(played, currentStrings(), lastAutoTargetName)
        // Web effect: remember the target while locked so hysteresis sticks.
        if (target != null && analysis.value.trackingState == "locked" &&
            target.name != lastAutoTargetName
        ) {
            lastAutoTargetName = target.name
        }
        return target
    }

    private fun targetMidi(target: NamedFrequency): Int = frequencyToMidiNote(target.freq) ?: 69

    private fun frameCents(): Double? {
        if (mode == TUNER_MODE_AUTO) return currentAutoTarget()?.cents
        val freq = analysis.value.frequency ?: return null
        if (freq <= 0) return null
        val strings = currentStrings()
        val target = strings.getOrNull(minOf(manualIndex, strings.size - 1)) ?: return null
        return centsFromMidiFloat(frequencyToMidiFloat(freq, refPitch()), targetMidi(target))
    }

    private fun inRange(): Boolean {
        val cents = frameCents() ?: return false
        return kotlin.math.abs(cents) <= tolerance()
    }

    private fun playedNoteLabel(): String? {
        val freq = analysis.value.frequency ?: return null
        if (freq <= 0 || !freq.isFinite()) return null
        val played = frequencyToMidiFloat(freq, refPitch())
        val nearest = nearestSemitone(played) ?: return null
        return midiNoteLabel(nearest)
    }

    private fun targetNoteLabel(): String? {
        if (mode == TUNER_MODE_MANUAL) {
            val cents = frameCents()
            val strings = currentStrings()
            val target = strings.getOrNull(minOf(manualIndex, strings.size - 1))
            if (cents != null && kotlin.math.abs(cents) < 50 && target != null) {
                val nominal = targetMidi(target)
                return midiNoteLabel(nominal)
            }
            return playedNoteLabel()
        }
        val target = currentAutoTarget() ?: return playedNoteLabel()
        val cents = frameCents()
        if (cents != null && kotlin.math.abs(cents) < 50) {
            return midiNoteLabel(target.midi)
        }
        return playedNoteLabel()
    }

    private fun resetHoldState() {
        holdJob?.cancel()
        holdJob = null
        releaseJob?.cancel()
        releaseJob = null
        confirmed = false
        autoTunedNames = emptyList()
        lastAutoTargetName = null
    }

    /** Direct port of the audio-monitor hold/release/confirm effect. */
    private fun evaluateHold() {
        val state = analysis.value.trackingState
        if (state != "locked") {
            holdJob?.cancel()
            holdJob = null
            if (state == "idle") {
                releaseJob?.cancel()
                releaseJob = null
                confirmed = false
            } else if (confirmed && releaseJob == null) {
                scheduleReleaseTimer()
            }
            return
        }

        val inRangeNow = inRange()
        if (confirmed) {
            if (inRangeNow) {
                releaseJob?.cancel()
                releaseJob = null
            } else if (releaseJob == null) {
                scheduleReleaseTimer()
            }
            return
        }

        releaseJob?.cancel()
        releaseJob = null

        val holding = holdJob != null
        if (!inRangeNow) {
            val cents = frameCents()
            val withinHysteresis = holding && cents != null &&
                kotlin.math.abs(cents) <= tolerance() + 1.5
            if (!withinHysteresis) {
                holdJob?.cancel()
                holdJob = null
                return
            }
        }

        val now = android.os.SystemClock.elapsedRealtime()
        val holdMs = currentSettings().inTune.holdMs.toLong()
        val elapsed = if (holding) now - holdStartedAt.get() else 0L
        if (shouldConfirm(inRangeNow, elapsed, holdMs)) {
            confirmLock()
            return
        }

        if (holdJob == null) holdStartedAt.set(now)
        val remaining = (holdMs - elapsed).coerceAtLeast(0)
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            delay(remaining)
            holdJob = null
            if (inRange()) confirmLock()
            rebuildUi()
        }
    }

    private fun scheduleReleaseTimer() {
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            delay(RELEASE_HYSTERESIS_MS)
            releaseJob = null
            if (confirmed && !inRange()) {
                confirmed = false
                rebuildUi()
            }
        }
    }

    private fun confirmLock() {
        holdJob?.cancel()
        holdJob = null
        releaseJob?.cancel()
        releaseJob = null
        if (confirmed) return

        confirmed = true
        haptics.success()
        val inTune = currentSettings().inTune
        if (inTune.enabled && inTune.sound) player.playChime()
        if (inTune.enabled && inTune.glow) triggerPulse()

        if (mode == TUNER_MODE_AUTO) {
            val target = currentAutoTarget()
            if (target != null && target.name !in autoTunedNames) {
                autoTunedNames = autoTunedNames + target.name
            }
        }
        rebuildUi()
    }

    private fun triggerPulse() {
        pulseActive = true
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            delay(LOCK_PULSE_DURATION_MS)
            pulseActive = false
            rebuildUi()
        }
    }

    fun rebuildUi() {
        evaluateHold()

        val settings = currentSettings()
        val strings = currentStrings()
        val tuning = registry.selectedTuning()
        val instrument = registry.selectedInstrument()
        val cents = frameCents()
        val inRangeNow = cents != null && kotlin.math.abs(cents) <= tolerance()
        val tracking = analysis.value.trackingState
        val isLocked = tracking == "locked"
        val showTuned = settings.inTune.enabled && confirmed
        val isTuned = if (settings.inTune.enabled) {
            showTuned
        } else {
            cents != null && isLocked && kotlin.math.abs(cents) <= tolerance()
        }
        val chipTuned = isTuned && activeStringName() != null

        val tuneColorHex = run {
            val centsValue = cents
            if (centsValue == null || kotlin.math.abs(centsValue) <= tolerance()) {
                null
            } else {
                interpolateColor(
                    settings.inTune.outOfTuneColor,
                    settings.inTune.color,
                    tuneColorProgress(centsValue, tolerance()),
                )
            }
        }

        val label = targetNoteLabel()
        val noteName = label?.dropLast(1)
        val noteOctave = label?.takeLast(1)?.toIntOrNull()

        val statusMessage = buildString {
            val error = _ui.value.captureError
            when {
                error != null -> append(error)
                !analysis.value.isCapturing -> append("IDLE")
                showTuned -> append("IN TUNE")
                isLocked -> {
                    val targetName =
                        if (mode == TUNER_MODE_MANUAL) {
                            currentStrings().getOrNull(manualIndex)?.name
                        } else {
                            currentAutoTarget()?.name
                        }
                    if (targetName != null) {
                        append("TUNING $targetName")
                    } else {
                        append("LISTENING FOR A NOTE")
                    }
                }
                else -> append("LISTENING FOR A NOTE")
            }
        }

        _ui.value = TunerUiState(
            isCapturing = analysis.value.isCapturing,
            frequency = analysis.value.frequency,
            trackingState = tracking,
            captureError = _ui.value.captureError,
            mode = mode,
            instruments = registry.instruments(),
            tunings = registry.availableTunings(),
            strings = strings,
            tuningSummary = strings.joinToString(" ") { it.name },
            instrumentLabel = instrument.label,
            tuningLabel = tuning.label,
            selectedInstrumentId = registry.selectedInstrumentIdFlow.value,
            selectedTuningId = registry.selectedTuningIdFlow.value,
            frameCents = cents,
            inRange = inRangeNow,
            confirmed = confirmed,
            pulseActive = pulseActive,
            autoTuned = autoTunedNames,
            activeString = activeStringName(),
            needlePercent = needlePercentFromCents(cents),
            tunePrompt = tuneDirectionText(cents, tolerance()),
            tuneCents = tuneCentsText(cents, tolerance()),
            tuneColorHex = tuneColorHex,
            isTuned = chipTuned,
            hzText = hzDisplay(analysis.value.frequency),
            noteName = noteName,
            noteOctave = noteOctave,
            statusMessage = statusMessage,
            manualIndex = manualIndex,
            inTune = settings.inTune,
        )
    }

    private fun activeStringName(): String? =
        if (mode == TUNER_MODE_MANUAL) {
            currentStrings().getOrNull(minOf(manualIndex, currentStrings().size - 1))?.name
        } else {
            currentAutoTarget()?.name
        }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "TunerViewModel"
    }
}
