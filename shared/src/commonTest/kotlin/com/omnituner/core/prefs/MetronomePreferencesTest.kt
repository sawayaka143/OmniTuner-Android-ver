package com.omnituner.core.prefs

import com.omnituner.core.metronome.DEFAULT_METRONOME_STATE
import com.omnituner.core.metronome.TempoRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomePreferencesTest {

    private fun setup(json: String? = null): Pair<MetronomePreferences, MemoryKeyValueStorage> {
        val storage = MemoryKeyValueStorage()
        json?.let { storage.setItem(METRONOME_STORAGE_KEY, it) }
        return MetronomePreferences(storage) to storage
    }

    @Test
    fun startsWithCountInOffAndDefaultRamp() {
        val (prefs, _) = setup()
        assertEquals(false, prefs.stateFlow.value.countIn)
        assertEquals(TempoRamp(enabled = false, targetBpm = 120.0, bars = 8), prefs.stateFlow.value.ramp)
    }

    @Test
    fun persistsCountInAndClampsRampValues() {
        val (prefs, storage) = setup()
        prefs.setCountIn(true)
        prefs.setRamp(enabled = true, targetBpm = 5000.0, bars = -4)

        val reloaded = MetronomePreferences(storage)
        assertEquals(true, reloaded.stateFlow.value.countIn)
        assertEquals(
            TempoRamp(enabled = true, targetBpm = 800.0, bars = 1),
            reloaded.stateFlow.value.ramp,
        )
    }

    @Test
    fun savesAppliesAndDeletesFullStatePresets() {
        val (prefs, _) = setup()
        prefs.setBpm(140.0)
        val preset = prefs.savePreset("Driving")
        assertTrue(preset != null)
        assertEquals("Driving", prefs.presetsFlow.value[0].name)
        assertEquals(140.0, prefs.presetsFlow.value[0].state.bpm)

        prefs.setBpm(90.0)
        prefs.applyPreset(preset!!.id)
        assertEquals(140.0, prefs.stateFlow.value.bpm)

        prefs.deletePreset(preset.id)
        assertEquals(emptyList(), prefs.presetsFlow.value)
    }

    @Test
    fun roundTripsPresetsThroughStorage() {
        val (prefs, storage) = setup()
        prefs.setCountIn(true)
        prefs.savePreset("Round trip")

        val reloaded = MetronomePreferences(storage)
        assertEquals(1, reloaded.presetsFlow.value.size)
        assertEquals("Round trip", reloaded.presetsFlow.value[0].name)
        assertEquals(true, reloaded.presetsFlow.value[0].state.countIn)
    }

    @Test
    fun ignoresUnknownPresetId() {
        val (prefs, _) = setup()
        prefs.setBpm(140.0)
        prefs.applyPreset("does-not-exist")
        assertEquals(140.0, prefs.stateFlow.value.bpm)
    }

    @Test
    fun loadsLegacyV2PayloadsWithCountInOffAndNoPresets() {
        val inner = MetronomePreferencesSchema.stateJsonObject(
            DEFAULT_METRONOME_STATE.copy(bpm = 150.0),
        )
        val json = buildString {
            append("{\"version\":2,\"state\":")
            append(inner.toString())
            append("}")
        }

        val (prefs, _) = setup(json)
        assertEquals(150.0, prefs.stateFlow.value.bpm)
        assertEquals(false, prefs.stateFlow.value.countIn)
        assertEquals(emptyList(), prefs.presetsFlow.value)
    }

    @Test
    fun parsesLegacyPatternShapes() {
        val storage = MemoryKeyValueStorage()
        val stateJson = MetronomePreferencesSchema.stateJsonObject(DEFAULT_METRONOME_STATE)
            .toString()
            .replace("\"barPattern\":[1]", "\"pattern\":[1,0]")
        storage.setItem(
            METRONOME_STORAGE_KEY,
            "{\"version\":1,\"state\":$stateJson}",
        )
        val prefs = MetronomePreferences(storage)
        assertEquals(listOf(1, 0), prefs.stateFlow.value.barPattern)

        val storage2 = MemoryKeyValueStorage()
        val stateJson2 = MetronomePreferencesSchema.stateJsonObject(DEFAULT_METRONOME_STATE)
            .toString()
            .replace("\"barPattern\":[1]", "\"barPatternBool\":[true,false,true]")
        storage2.setItem(
            METRONOME_STORAGE_KEY,
            "{\"version\":1,\"state\":$stateJson2}",
        )
        val prefs2 = MetronomePreferences(storage2)
        assertEquals(listOf(1, 0, 1), prefs2.stateFlow.value.barPattern)
    }

    @Test
    fun parsesPolyLegacyRatio() {
        val storage = MemoryKeyValueStorage()
        val stateJson = MetronomePreferencesSchema.stateJsonObject(DEFAULT_METRONOME_STATE)
            .toString()
            .replace(
                "\"poly\":{\"enabled\":false,\"events\":3,\"accentFirst\":true}",
                "\"polyLegacy\":{\"enabled\":true,\"ratio\":{\"a\":4,\"b\":7}}",
            )
        storage.setItem(METRONOME_STORAGE_KEY, "{\"version\":2,\"state\":$stateJson}")
        val prefs = MetronomePreferences(storage)
        assertEquals(true, prefs.stateFlow.value.poly.enabled)
        assertEquals(7, prefs.stateFlow.value.poly.events)
        assertEquals(true, prefs.stateFlow.value.poly.accentFirst)
    }

    @Test
    fun legacyMasterVolumeAliasIsAccepted() {
        val storage = MemoryKeyValueStorage()
        val stateJson = MetronomePreferencesSchema.stateJsonObject(DEFAULT_METRONOME_STATE)
            .toString()
            .replace("\"masterVol\":0.9", "\"masterVolume\":0.5")
        storage.setItem(METRONOME_STORAGE_KEY, "{\"version\":2,\"state\":$stateJson}")
        val prefs = MetronomePreferences(storage)
        assertEquals(0.5, prefs.stateFlow.value.masterVol)
    }

    @Test
    fun invalidSoundRoleIdsFallBackToDefaults() {
        val storage = MemoryKeyValueStorage()
        val stateJson = MetronomePreferencesSchema.stateJsonObject(DEFAULT_METRONOME_STATE)
            .toString()
            .replace("\"beat\":{\"id\":\"beep-mid\",\"vol\":0.8}", "\"beat\":{\"id\":\"nope\",\"vol\":0.4}")
        storage.setItem(METRONOME_STORAGE_KEY, "{\"version\":3,\"state\":$stateJson}")
        val prefs = MetronomePreferences(storage)
        assertEquals("beep-mid", prefs.stateFlow.value.sounds.beat.id)
        assertEquals(0.4, prefs.stateFlow.value.sounds.beat.vol)
    }

    @Test
    fun rejectsInvalidBarPatternsAndMeters() {
        val (prefs, _) = setup()
        prefs.setBarPattern(emptyList())
        assertEquals(DEFAULT_METRONOME_STATE.barPattern, prefs.stateFlow.value.barPattern)

        prefs.setBarPattern(listOf(2))
        assertEquals(DEFAULT_METRONOME_STATE.barPattern, prefs.stateFlow.value.barPattern)

        prefs.setTimeSignature(0, 4)
        assertEquals(4, prefs.stateFlow.value.timeSignature.numerator)

        prefs.setTimeSignature(5, 3)
        assertEquals(4, prefs.stateFlow.value.timeSignature.denominator)

        prefs.setSoundRole("beat", "not-a-sound")
        assertEquals("beep-mid", prefs.stateFlow.value.sounds.beat.id)
    }
}
