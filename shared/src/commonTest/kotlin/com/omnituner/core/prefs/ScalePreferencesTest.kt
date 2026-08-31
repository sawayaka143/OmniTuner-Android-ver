package com.omnituner.core.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScalePreferencesTest {

    private fun setup(json: String? = null): Pair<ScalePreferences, MemoryKeyValueStorage> {
        val storage = MemoryKeyValueStorage()
        json?.let { storage.setItem(SCALE_PREFERENCES_STORAGE_KEY, it) }
        return ScalePreferences(storage) to storage
    }

    @Test
    fun defaultsMatchWebConstants() {
        val (prefs, _) = setup()
        assertEquals(DEFAULT_SCALE_PREFERENCES, prefs.stateFlow.value)
        assertEquals(4, DEFAULT_SCALE_PREFERENCES.rootPitchClass)
        assertEquals("major", DEFAULT_SCALE_PREFERENCES.scaleId)
        assertEquals(12, DEFAULT_SCALE_PREFERENCES.fretCount)
        assertEquals("#ede8d0", DEFAULT_SCALE_PREFERENCES.accent)
    }

    @Test
    fun rejectsWrongVersionOrShape() {
        val (prefs, _) = setup("{\"version\":2,\"state\":{}}")
        assertEquals(DEFAULT_SCALE_PREFERENCES, prefs.stateFlow.value)

        val (prefs2, _) = setup("{\"version\":1,\"state\":{\"rootPitchClass\":99}}")
        assertEquals(DEFAULT_SCALE_PREFERENCES.rootPitchClass, prefs2.stateFlow.value.rootPitchClass)
    }

    @Test
    fun validatesFieldByField() {
        val json = """
            {
              "version": 1,
              "state": {
                "rootPitchClass": 9,
                "scaleId": "blues",
                "accidental": "flat",
                "fretCount": 15,
                "labelMode": "scale-degrees",
                "showOutsideScale": true,
                "accent": "#ABCDEF",
                "rootNoteColor": "bogus",
                "noteColor": "#112233",
                "bgColor": "#445566",
                "workbenchScale": 9
              }
            }
        """.trimIndent()

        val (prefs, _) = setup(json)
        val state = prefs.stateFlow.value
        assertEquals(9, state.rootPitchClass)
        assertEquals("blues", state.scaleId)
        assertEquals("flat", state.accidental)
        assertEquals(15, state.fretCount)
        assertEquals("scale-degrees", state.labelMode)
        assertEquals(true, state.showOutsideScale)
        assertEquals("#abcdef", state.accent)
        assertEquals(DEFAULT_SCALE_PREFERENCES.rootNoteColor, state.rootNoteColor)
        assertEquals("#112233", state.noteColor)
        assertEquals("#445566", state.bgColor)
        assertEquals(1.3, state.workbenchScale, 1e-12)
    }

    @Test
    fun upgradesLegacyColors() {
        val json = """
            {
              "version": 1,
              "state": {
                "accent": "#ffffff",
                "rootNoteColor": "#ffffff",
                "noteColor": "#2e2e28"
              }
            }
        """.trimIndent()

        val (prefs, _) = setup(json)
        assertEquals("#ede8d0", prefs.stateFlow.value.accent)
        assertEquals("#ede8d0", prefs.stateFlow.value.rootNoteColor)
        assertEquals("#3b3b3b", prefs.stateFlow.value.noteColor)
    }

    @Test
    fun setterValidation() {
        val (prefs, _) = setup()

        prefs.setRootPitchClass(13)
        assertEquals(4, prefs.stateFlow.value.rootPitchClass)
        prefs.setRootPitchClass(7)
        assertEquals(7, prefs.stateFlow.value.rootPitchClass)

        prefs.setScaleId("not-a-scale")
        assertEquals("major", prefs.stateFlow.value.scaleId)

        prefs.setFretCount(16)
        assertEquals(12, prefs.stateFlow.value.fretCount)

        prefs.setFretCount(21)
        assertEquals(21, prefs.stateFlow.value.fretCount)

        prefs.setAccent("red")
        assertEquals("#ede8d0", prefs.stateFlow.value.accent)

        prefs.setWorkbenchScale(9.0)
        assertEquals(1.3, prefs.stateFlow.value.workbenchScale, 1e-12)

        prefs.setWorkbenchScale(0.1)
        assertEquals(0.75, prefs.stateFlow.value.workbenchScale, 1e-12)

        prefs.resetWorkbenchScale()
        assertEquals(1.0, prefs.stateFlow.value.workbenchScale, 1e-12)
    }

    @Test
    fun snapsWorkbenchScaleToStep() {
        val (prefs, _) = setup()
        prefs.setWorkbenchScale(1.07)
        assertEquals(1.05, prefs.stateFlow.value.workbenchScale, 1e-9)
    }

    @Test
    fun roundTripsThroughStorage() {
        val (prefs, storage) = setup()
        prefs.setScaleId("dorian")
        prefs.setAccent("#aabbcc")
        prefs.setBgColor("#001122")
        prefs.setCardColor(null)

        val reloaded = ScalePreferences(storage)
        assertEquals("dorian", reloaded.stateFlow.value.scaleId)
        assertEquals("#aabbcc", reloaded.stateFlow.value.accent)
        assertEquals("#001122", reloaded.stateFlow.value.bgColor)
        assertEquals(null, reloaded.stateFlow.value.cardColor)
    }

    @Test
    fun tuningMidiBoundsMatchWeb() {
        assertEquals(24, MIN_TUNING_MIDI_NOTE)
        assertEquals(84, MAX_TUNING_MIDI_NOTE)
    }

    @Test
    fun serializedShapeUsesVersionOne() {
        val json = ScalePreferencesSchema.serialize(DEFAULT_SCALE_PREFERENCES)
        assertTrue(json.contains("\"version\":1"))
    }
}
