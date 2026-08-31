package com.omnituner.android.ui.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnituner.android.audio.MetronomeAudioEngine
import com.omnituner.core.metronome.DEFAULT_METRONOME_STATE
import com.omnituner.core.metronome.MetronomePreset
import com.omnituner.core.metronome.MetronomeSoundRoles
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.PolyState
import com.omnituner.core.metronome.SoundRole
import com.omnituner.core.metronome.TempoRamp
import com.omnituner.core.metronome.TimeSignature
import com.omnituner.core.prefs.MetronomePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MetronomeUiState(
    val state: MetronomeState = DEFAULT_METRONOME_STATE,
    val presets: List<MetronomePreset> = emptyList(),
    val isPlaying: Boolean = false,
    val barIndex: Int = 0,
    val patternPos: Int = 0,
    val progress: Double = 0.0,
    val countIn: Boolean = false,
    val barActive: Boolean = true,
)

class MetronomeViewModel(private val prefs: MetronomePreferences) : ViewModel() {

    private val engine = MetronomeAudioEngine()

    private val _ui = MutableStateFlow(
        MetronomeUiState(
            state = prefs.stateFlow.value,
            presets = prefs.presetsFlow.value,
        ),
    )
    val ui: StateFlow<MetronomeUiState> = _ui.asStateFlow()

    init {
        engine.configure(_ui.value.state)
        engine.setTransportListener { barIndex, patternPos, progress, countIn, barActive ->
            _ui.value = _ui.value.copy(
                barIndex = barIndex,
                patternPos = patternPos,
                progress = progress,
                countIn = countIn,
                barActive = barActive,
            )
        }
    }

    fun toggle() {
        if (_ui.value.isPlaying) {
            engine.stop()
            _ui.value = _ui.value.copy(isPlaying = false, progress = 0.0)
        } else {
            engine.configure(_ui.value.state)
            engine.start()
            _ui.value = _ui.value.copy(isPlaying = true)
        }
    }

    fun onAppBackground(isBackground: Boolean) {
        engine.setBackground(isBackground)
    }

    fun previewVoice(id: String, vol: Double) {
        engine.previewVoice(id, vol)
    }

    fun setBpm(bpm: Double) {
        update { it.copy(bpm = bpm.coerceIn(1.0, 800.0)) }
    }

    fun setTimeSignature(numerator: Int, denominator: Int) {
        update { it.copy(timeSignature = TimeSignature(numerator, denominator)) }
    }

    fun setDivisionsPerBeat(value: Int) {
        update { it.copy(divisionsPerBeat = value.coerceIn(1, 12)) }
    }

    fun setBarPattern(pattern: List<Int>) {
        update { it.copy(barPattern = pattern) }
    }

    fun setPoly(enabled: Boolean? = null, events: Int? = null, accentFirst: Boolean? = null) {
        val current = _ui.value.state.poly
        update {
            it.copy(
                poly = PolyState(
                    enabled = enabled ?: current.enabled,
                    events = (events ?: current.events).coerceIn(1, 32),
                    accentFirst = accentFirst ?: current.accentFirst,
                ),
            )
        }
    }

    fun setSoundRole(role: String, id: String, vol: Double? = null, accentVol: Double? = null) {
        update { state ->
            val current = when (role) {
                "downbeat" -> state.sounds.downbeat
                "beat" -> state.sounds.beat
                "subdivision" -> state.sounds.subdivision
                else -> state.sounds.poly
            }
            val next = SoundRole(
                id = id,
                vol = vol ?: current.vol,
                accentVol = accentVol ?: current.accentVol,
            )
            state.copy(
                sounds = MetronomeSoundRoles(
                    downbeat = if (role == "downbeat") next else state.sounds.downbeat,
                    beat = if (role == "beat") next else state.sounds.beat,
                    subdivision = if (role == "subdivision") next else state.sounds.subdivision,
                    poly = if (role == "poly") next else state.sounds.poly,
                ),
            )
        }
    }

    fun setMasterVol(volume: Double) {
        update { it.copy(masterVol = volume.coerceIn(0.0, 1.0)) }
    }

    fun setCountIn(enabled: Boolean) {
        update { it.copy(countIn = enabled) }
    }

    fun setRamp(enabled: Boolean? = null, targetBpm: Double? = null, bars: Int? = null) {
        val current = _ui.value.state.ramp
        update {
            it.copy(
                ramp = TempoRamp(
                    enabled = enabled ?: current.enabled,
                    targetBpm = targetBpm ?: current.targetBpm,
                    bars = bars ?: current.bars,
                ),
            )
        }
    }

    fun savePreset(name: String) {
        val id = "mp-${System.nanoTime().toString(36)}"
        val finalName = name.trim().ifBlank { "Preset ${_ui.value.presets.size + 1}" }
        val preset = MetronomePreset(id, finalName, _ui.value.state)
        val next = (listOf(preset) + _ui.value.presets).take(50)
        _ui.value = _ui.value.copy(presets = next)
        prefs.replacePresets(next)
    }

    fun applyPreset(id: String) {
        val preset = _ui.value.presets.find { it.id == id } ?: return
        update { preset.state }
    }

    fun deletePreset(id: String) {
        val next = _ui.value.presets.filter { it.id != id }
        _ui.value = _ui.value.copy(presets = next)
        prefs.replacePresets(next)
    }

    private fun update(transform: (MetronomeState) -> MetronomeState) {
        val next = transform(_ui.value.state)
        _ui.value = _ui.value.copy(state = next)
        engine.configure(next)
        prefs.replaceState(next)
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    companion object {
        fun factory(prefs: MetronomePreferences): ViewModelProvider.Factory = viewModelFactory {
            initializer { MetronomeViewModel(prefs) }
        }
    }
}
