package com.omnituner.core.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunerPreferencesTest {

    private fun serviceWithStorage(json: String? = null): Pair<TunerPreferences, MemoryKeyValueStorage> {
        val storage = MemoryKeyValueStorage()
        json?.let { storage.setItem(TUNER_PREFERENCES_STORAGE_KEY, it) }
        return TunerPreferences(storage) to storage
    }

    @Test
    fun startsWithDefaultSettings() {
        val (service, _) = serviceWithStorage()
        assertEquals(DEFAULT_TUNER_SETTINGS, service.tunerSettings.value)
    }

    @Test
    fun fallsBackSafelyOnMalformedJson() {
        val (service, _) = serviceWithStorage("{not-json")
        assertEquals(DEFAULT_TUNER_SETTINGS, service.tunerSettings.value)
    }

    @Test
    fun restoresAndClampsOutOfRangeValuesOnLoad() {
        val json = """
            {
              "version": 3,
              "tuner": {
                "mode": "manual",
                "startupMode": "auto",
                "referencePitch": 500,
                "inTune": {
                  "enabled": false,
                  "sound": false,
                  "glow": true,
                  "color": "#EE6600",
                  "outOfTuneColor": "#3366AA",
                  "tolerance": 900,
                  "holdMs": -42
                }
              }
            }
        """.trimIndent()

        val (service, _) = serviceWithStorage(json)
        assertEquals(
            TunerSettings(
                mode = "manual",
                startupMode = "auto",
                referencePitch = 466,
                inTune = InTunePreferences(
                    enabled = false,
                    sound = false,
                    glow = true,
                    color = "#ee6600",
                    outOfTuneColor = "#3366aa",
                    tolerance = 15,
                    holdMs = 0,
                ),
            ),
            service.tunerSettings.value,
        )
    }

    @Test
    fun persistsSettingsRoundTrip() {
        val (service, storage) = serviceWithStorage()
        service.setMode("manual")
        service.setStartupMode("remember")
        service.setInTuneEnabled(false)
        service.setInTuneSound(false)
        service.setInTuneGlow(true)
        service.setInTuneColor("#ff9900")
        service.setOutOfTuneColor("#00aacc")
        service.setInTuneTolerance(12.0)
        service.setInTuneHoldMs(800.0)
        service.setReferencePitch(442.0)

        val restored = TunerPreferences(storage)

        assertEquals(
            TunerSettings(
                mode = "manual",
                startupMode = "remember",
                referencePitch = 442,
                inTune = InTunePreferences(
                    enabled = false,
                    sound = false,
                    glow = true,
                    color = "#ff9900",
                    outOfTuneColor = "#00aacc",
                    tolerance = 12,
                    holdMs = 800,
                ),
            ),
            restored.tunerSettings.value,
        )
    }

    @Test
    fun validatesSetterInputs() {
        val (service, _) = serviceWithStorage()

        service.setInTuneColor("not-a-color")
        assertEquals(DEFAULT_TUNER_SETTINGS.inTune.color, service.tunerSettings.value.inTune.color)

        service.setOutOfTuneColor("not-a-color")
        assertEquals(
            DEFAULT_TUNER_SETTINGS.inTune.outOfTuneColor,
            service.tunerSettings.value.inTune.outOfTuneColor,
        )

        service.setMode("magic")
        assertEquals("auto", service.tunerSettings.value.mode)

        service.setInTuneTolerance(99.0)
        assertEquals(15, service.tunerSettings.value.inTune.tolerance)

        service.setInTuneTolerance(0.2)
        assertEquals(1, service.tunerSettings.value.inTune.tolerance)

        service.setInTuneHoldMs(10000.0)
        assertEquals(1500, service.tunerSettings.value.inTune.holdMs)
    }

    @Test
    fun referencePitchDefaultsAndClamps() {
        val (service, storage) = serviceWithStorage()
        assertEquals(440, service.tunerSettings.value.referencePitch)

        service.setReferencePitch(432.0)
        assertEquals(432, service.tunerSettings.value.referencePitch)
        assertEquals(432, TunerPreferences(storage).tunerSettings.value.referencePitch)

        val (clampLow, _) = serviceWithStorage()
        clampLow.setReferencePitch(300.0)
        assertEquals(415, clampLow.tunerSettings.value.referencePitch)

        val (clampHigh, _) = serviceWithStorage()
        clampHigh.setReferencePitch(500.0)
        assertEquals(466, clampHigh.tunerSettings.value.referencePitch)

        val (nonFinite, _) = serviceWithStorage()
        nonFinite.setReferencePitch(Double.NaN)
        assertEquals(440, nonFinite.tunerSettings.value.referencePitch)
        nonFinite.setReferencePitch(Double.POSITIVE_INFINITY)
        assertEquals(440, nonFinite.tunerSettings.value.referencePitch)

        val (rounding, _) = serviceWithStorage()
        rounding.setReferencePitch(442.7)
        assertEquals(443, rounding.tunerSettings.value.referencePitch)
    }

    @Test
    fun keepsSessionChangesWhenStorageFails() {
        val storage = ThrowingKeyValueStorage(MemoryKeyValueStorage())
        val service = TunerPreferences(storage)

        service.setReferencePitch(432.0)

        assertEquals(432, service.tunerSettings.value.referencePitch)
    }

    @Test
    fun worksWithoutStorage() {
        val service = TunerPreferences(null)
        service.setReferencePitch(432.0)
        assertEquals(432, service.tunerSettings.value.referencePitch)
    }

    @Test
    fun serializedShapeUsesVersionFour() {
        val json = TunerPreferencesSchema.serialize(DEFAULT_TUNER_SETTINGS)
        assertTrue(json.contains("\"version\":4"))
        assertTrue(json.contains("\"tuner\""))
    }
}
