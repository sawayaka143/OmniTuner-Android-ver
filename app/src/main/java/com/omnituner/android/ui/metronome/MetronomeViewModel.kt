package com.omnituner.android.ui.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.audio.MetronomeAudioEngine
import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import com.omnituner.core.metronome.DEFAULT_METRONOME_STATE
import com.omnituner.core.metronome.DIVISIONS_MAX
import com.omnituner.core.metronome.DIVISIONS_MIN
import com.omnituner.core.metronome.MetronomePreset
import com.omnituner.core.metronome.MetronomeSoundRoles
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.POLY_MAX
import com.omnituner.core.metronome.POLY_MIN
import com.omnituner.core.metronome.PRESETS_MAX
import com.omnituner.core.metronome.RAMP_BARS_MAX
import com.omnituner.core.metronome.RAMP_BARS_MIN
import com.omnituner.core.metronome.SoundRole
import com.omnituner.core.metronome.TimeSignature
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SoundSlot(val label: String) {
    Downbeat("Downbeat"),
    Beat("Beat"),
    Subdivision("Subdivision"),
    Poly("Poly"),
}

fun MetronomeSoundRoles.role(slot: SoundSlot): SoundRole = when (slot) {
    SoundSlot.Downbeat -> downbeat
    SoundSlot.Beat -> beat
    SoundSlot.Subdivision -> subdivision
    SoundSlot.Poly -> poly
}

private fun MetronomeSoundRoles.replace(slot: SoundSlot, role: SoundRole): MetronomeSoundRoles = when (slot) {
    SoundSlot.Downbeat -> copy(downbeat = role)
    SoundSlot.Beat -> copy(beat = role)
    SoundSlot.Subdivision -> copy(subdivision = role)
    SoundSlot.Poly -> copy(poly = role)
}

data class MetronomeUiState(
    val state: MetronomeState = DEFAULT_METRONOME_STATE,
    val presets: List<MetronomePreset> = emptyList(),
    val isPlaying: Boolean = false,
    val barIndex: Int = 0,
    val patternPos: Int = 0,
    val progress: Double = 0.0,
    val inCountIn: Boolean = false,
    val barActive: Boolean = true,
)

private data class Transport(
    val barIndex: Int = 0,
    val patternPos: Int = 0,
    val progress: Double = 0.0,
    val inCountIn: Boolean = false,
    val barActive: Boolean = true,
)

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container
    private val store = container.metronome
    private val presetStore = container.metronomePresets
    private val engine = MetronomeAudioEngine()
    private val playing = MutableStateFlow(false)
    private val transport = MutableStateFlow(Transport())

    val ui: StateFlow<MetronomeUiState> = combine(
        store.flow,
        presetStore.flow,
        playing,
        transport,
    ) { state, presets, isPlaying, t ->
        MetronomeUiState(
            state = state,
            presets = presets,
            isPlaying = isPlaying,
            barIndex = t.barIndex,
            patternPos = t.patternPos,
            progress = t.progress,
            inCountIn = t.inCountIn,
            barActive = t.barActive,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MetronomeUiState())

    init {
        engine.setTransportListener { barIndex, patternPos, progress, countIn, barActive ->
            transport.value = Transport(barIndex, patternPos, progress, countIn, barActive)
        }
        store.flow.onEach { engine.configure(it) }.launchIn(viewModelScope)
    }

    fun toggle() {
        if (playing.value) {
            engine.stop()
            playing.value = false
            transport.value = Transport()
        } else {
            engine.configure(store.flow.value)
            engine.start()
            playing.value = true
        }
    }

    fun setBackground(isBackground: Boolean) {
        engine.setBackground(isBackground)
    }

    fun previewVoice(id: String, vol: Double) {
        engine.previewVoice(id, vol)
    }

    fun setBpm(bpm: Double) {
        edit { it.copy(bpm = bpm.coerceIn(BPM_MIN.toDouble(), BPM_MAX.toDouble())) }
    }

    fun changeBpm(delta: Int) {
        edit { it.copy(bpm = (it.bpm + delta).coerceIn(BPM_MIN.toDouble(), BPM_MAX.toDouble())) }
    }

    fun setTimeSignature(numerator: Int, denominator: Int) {
        edit { it.copy(timeSignature = TimeSignature(numerator, denominator)) }
    }

    fun changeNumerator(delta: Int) {
        edit {
            val current = it.timeSignature
            it.copy(timeSignature = current.copy(numerator = (current.numerator + delta).coerceIn(1, 32)))
        }
    }

    fun setDivisionsPerBeat(value: Int) {
        edit { it.copy(divisionsPerBeat = value.coerceIn(DIVISIONS_MIN, DIVISIONS_MAX)) }
    }

    fun changeDivisionsPerBeat(delta: Int) {
        edit { it.copy(divisionsPerBeat = (it.divisionsPerBeat + delta).coerceIn(DIVISIONS_MIN, DIVISIONS_MAX)) }
    }

    fun setBarPattern(pattern: List<Int>) {
        edit { it.copy(barPattern = pattern) }
    }

    fun setPolyEnabled(enabled: Boolean) {
        edit { it.copy(poly = it.poly.copy(enabled = enabled)) }
    }

    fun setPolyAccentFirst(accentFirst: Boolean) {
        edit { it.copy(poly = it.poly.copy(accentFirst = accentFirst)) }
    }

    fun changePolyEvents(delta: Int) {
        edit { it.copy(poly = it.poly.copy(events = (it.poly.events + delta).coerceIn(POLY_MIN, POLY_MAX))) }
    }

    fun setVoice(slot: SoundSlot, voiceId: String) {
        edit {
            val role = it.sounds.role(slot).copy(id = voiceId)
            it.copy(sounds = it.sounds.replace(slot, role))
        }
    }

    fun setSlotVolume(slot: SoundSlot, volume: Double) {
        edit {
            val role = it.sounds.role(slot).copy(vol = volume.coerceIn(0.0, 1.0))
            it.copy(sounds = it.sounds.replace(slot, role))
        }
    }

    fun setMasterVolume(volume: Double) {
        edit { it.copy(masterVol = volume.coerceIn(0.0, 1.0)) }
    }

    fun setCountIn(enabled: Boolean) {
        edit { it.copy(countIn = enabled) }
    }

    fun setRampEnabled(enabled: Boolean) {
        edit { it.copy(ramp = it.ramp.copy(enabled = enabled)) }
    }

    fun changeRampTargetBpm(delta: Int) {
        edit {
            it.copy(ramp = it.ramp.copy(targetBpm = (it.ramp.targetBpm + delta).coerceIn(BPM_MIN.toDouble(), BPM_MAX.toDouble())))
        }
    }

    fun changeRampBars(delta: Int) {
        edit { it.copy(ramp = it.ramp.copy(bars = (it.ramp.bars + delta).coerceIn(RAMP_BARS_MIN, RAMP_BARS_MAX))) }
    }

    fun savePreset(name: String) {
        val preset = MetronomePreset(
            id = "mp-${UUID.randomUUID()}",
            name = name.trim().ifBlank { "Preset ${presetStore.flow.value.size + 1}" },
            state = store.flow.value,
        )
        viewModelScope.launch {
            presetStore.update { (listOf(preset) + it).take(PRESETS_MAX) }
        }
    }

    fun applyPreset(id: String) {
        val preset = presetStore.flow.value.firstOrNull { it.id == id } ?: return
        edit { preset.state }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presetStore.update { list -> list.filterNot { it.id == id } }
        }
    }

    private fun edit(transform: (MetronomeState) -> MetronomeState) {
        viewModelScope.launch { store.update(transform) }
    }

    override fun onCleared() {
        engine.stop()
    }
}
