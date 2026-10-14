package com.omnituner.android.ui.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.data.HOLD_MS_RANGE
import com.omnituner.android.data.REFERENCE_PITCH_RANGE
import com.omnituner.android.data.StartupMode
import com.omnituner.android.data.TOLERANCE_RANGE
import com.omnituner.android.data.ThemeMode
import com.omnituner.android.data.TunerSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmniTunerApp).container

    val theme: StateFlow<ThemeMode> = container.theme.flow
    val tuner: StateFlow<TunerSettings> = container.tuner.flow

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.theme.update { mode } }
    }

    fun setStartupMode(mode: StartupMode) {
        edit { it.copy(startupMode = mode) }
    }

    fun changeReferencePitch(delta: Int) {
        edit { it.copy(referencePitch = (it.referencePitch + delta).coerceIn(REFERENCE_PITCH_RANGE)) }
    }

    fun changeTolerance(delta: Int) {
        edit { it.copy(tolerance = (it.tolerance + delta).coerceIn(TOLERANCE_RANGE)) }
    }

    fun setHoldMs(holdMs: Int) {
        edit { it.copy(holdMs = holdMs.coerceIn(HOLD_MS_RANGE)) }
    }

    fun setInTuneFeedback(enabled: Boolean) {
        edit { it.copy(inTuneFeedback = enabled) }
    }

    fun setChime(enabled: Boolean) {
        edit { it.copy(chime = enabled) }
    }

    fun setGlow(enabled: Boolean) {
        edit { it.copy(glow = enabled) }
    }

    private fun edit(transform: (TunerSettings) -> TunerSettings) {
        viewModelScope.launch { container.tuner.update(transform) }
    }
}
