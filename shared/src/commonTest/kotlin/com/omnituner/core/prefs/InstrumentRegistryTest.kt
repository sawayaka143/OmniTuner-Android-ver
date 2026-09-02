package com.omnituner.core.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstrumentRegistryTest {

    @Test
    fun startsWithBuiltInInstrumentsAndSelection() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())
        assertEquals("guitar", registry.selectedInstrument().id)
        assertEquals("standard", registry.selectedTuning().id)
        assertEquals(2, registry.instruments().size)
        assertEquals(4, registry.availableTunings().size)
    }

    @Test
    fun validatesCustomInstrumentInputs() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())

        assertFailsWith<InstrumentNameException> {
            registry.createInstrument("  ", 6, List(6) { 40 })
        }
        assertFailsWith<InstrumentNameException> {
            registry.createInstrument("x".repeat(31), 6, List(6) { 40 })
        }
        assertFailsWith<InstrumentNameException> {
            registry.createInstrument("Seven", 13, List(13) { 40 })
        }
        assertFailsWith<InstrumentNameException> {
            registry.createInstrument("Bad", 6, List(6) { 10 })
        }
        assertFailsWith<InstrumentNameException> {
            registry.createInstrument("Short", 6, List(5) { 40 })
        }
    }

    @Test
    fun createsUpdatesAndDeletesCustomInstruments() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())

        val instrument = registry.createInstrument("Baritone", 6, listOf(35, 40, 45, 50, 55, 59))
        assertEquals("Baritone", instrument.label)
        assertEquals("custom", instrument.kind)
        assertEquals(3, registry.instruments().size)

        registry.selectInstrument(instrument.id)
        val defaultTuning = registry.availableTunings().first()
        assertEquals("B1", defaultTuning.strings[0].name)
        assertEquals(61.74, defaultTuning.strings[0].freq, 0.01)

        registry.updateInstrument(instrument.id, "Bari", 6, listOf(35, 40, 45, 50, 55, 59))
        assertEquals("Bari", registry.instruments().last().label)

        registry.deleteInstrument(instrument.id)
        assertEquals(2, registry.instruments().size)
    }

    @Test
    fun deletingSelectedInstrumentResetsSelection() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())
        val instrument = registry.createInstrument("Weird", 4, listOf(40, 45, 50, 55))
        registry.selectInstrument(instrument.id)
        assertEquals(instrument.id, registry.selectedInstrumentIdFlow.value)

        registry.deleteInstrument(instrument.id)
        assertEquals("guitar", registry.selectedInstrumentIdFlow.value)
        assertEquals("standard", registry.selectedTuningIdFlow.value)
    }

    @Test
    fun validatesCustomTunings() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())

        assertFailsWith<InstrumentNameException> {
            registry.createTuning("guitar", "", listOf(40, 45, 50, 55, 59, 64))
        }
        assertFailsWith<InstrumentNameException> {
            registry.createTuning("guitar", "Drop", listOf(38, 45, 50, 55, 59, 64, 69))
        }
        assertFailsWith<InstrumentNameException> {
            registry.createTuning("no-such-instrument", "Drop", listOf(38, 45, 50, 55, 59, 64))
        }

        val tuning = registry.createTuning("guitar", "Drop D", listOf(38, 45, 50, 55, 59, 64))
        val runtime = registry.availableTunings().first { it.id == tuning.id }
        assertEquals("D2", runtime.strings[0].name)

        registry.selectTuning(tuning.id)
        assertEquals(tuning.id, registry.selectedTuningIdFlow.value)
    }

    @Test
    fun tuningsScopedToInstrument() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())
        registry.createTuning("guitar", "Guitar custom", listOf(40, 45, 50, 55, 59, 64))
        registry.createTuning("ukulele", "Uke custom", listOf(67, 60, 64, 69))

        assertEquals(1, registry.tuningsForInstrument("guitar").size)
        assertEquals(1, registry.tuningsForInstrument("ukulele").size)
        assertEquals(0, registry.tuningsForInstrument("nope").size)

        registry.selectInstrument("ukulele")
        val tunings = registry.availableTunings()
        assertEquals(2, tunings.size)
        assertTrue(tunings.any { it.label == "Uke custom" })
    }

    @Test
    fun deletingTuningFallsBackToFirstTuning() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())
        val tuning = registry.createTuning("guitar", "Drop D", listOf(38, 45, 50, 55, 59, 64))
        registry.selectTuning(tuning.id)

        registry.deleteTuning(tuning.id)
        assertEquals("standard", registry.selectedTuningIdFlow.value)
    }

    @Test
    fun unknownSelectionFallsBackToGuitar() {
        val storage = MemoryKeyValueStorage()
        storage.setItem(
            INSTRUMENT_REGISTRY_STORAGE_KEY,
            """{"version":1,"customInstruments":[],"customTunings":[],"""" +
                "selectedInstrumentId\":\"nope\",\"selectedTuningId\":\"nope\"}",
        )
        val registry = InstrumentRegistry(storage)
        assertEquals("guitar", registry.selectedInstrument().id)
        assertEquals("standard", registry.selectedTuning().id)
    }

    @Test
    fun roundTripsCustomDataThroughStorage() {
        val storage = MemoryKeyValueStorage()
        val registry = InstrumentRegistry(storage)
        val instrument = registry.createInstrument("Nashville", 6, listOf(52, 57, 62, 67, 59, 64))
        registry.createTuning("guitar", "Drop D", listOf(38, 45, 50, 55, 59, 64))
        registry.selectInstrument(instrument.id)

        val reloaded = InstrumentRegistry(storage)
        assertEquals(3, reloaded.instruments().size)
        assertEquals(instrument.id, reloaded.selectedInstrumentIdFlow.value)
        assertEquals(1, reloaded.tuningsForInstrument("guitar").size)
    }

    @Test
    fun migratesLegacyTuningsFromOldTunerKey() {
        val storage = MemoryKeyValueStorage()
        storage.setItem(
            OLD_TUNER_PREFERENCES_KEY,
            """
            {
              "version": 4,
              "tuner": {},
              "tunings": [
                {
                  "id": "custom-legacy1",
                  "instrumentId": "guitar",
                  "name": "Legacy",
                  "notes": [38, 45, 50, 55, 59, 64]
                }
              ]
            }
            """.trimIndent(),
        )

        val registry = InstrumentRegistry(storage)
        assertEquals(1, registry.tuningsForInstrument("guitar").size)
        assertEquals("Legacy", registry.tuningsForInstrument("guitar")[0].name)
        assertTrue(storage.getItem(INSTRUMENT_REGISTRY_STORAGE_KEY) != null)
    }

    @Test
    fun rejectsBadIdsOnLoad() {
        val storage = MemoryKeyValueStorage()
        storage.setItem(
            INSTRUMENT_REGISTRY_STORAGE_KEY,
            """
            {
              "version": 1,
              "customInstruments": [
                { "id": "bad-id", "name": "Nope", "stringCount": 6, "defaultNotes": [40,40,40,40,40,40] }
              ],
              "customTunings": [],
              "selectedInstrumentId": "guitar",
              "selectedTuningId": "standard"
            }
            """.trimIndent(),
        )
        val registry = InstrumentRegistry(storage)
        assertEquals(2, registry.instruments().size)
    }

    @Test
    fun generatesWebStyleUuidIds() {
        val registry = InstrumentRegistry(MemoryKeyValueStorage())
        val instrument = registry.createInstrument("Nashville", 6, List(6) { 40 })
        val tuning = registry.createTuning("guitar", "Drop D", listOf(38, 45, 50, 55, 59, 64))

        val uuidShape = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
        assertTrue(
            Regex("^instr-$uuidShape$").matches(instrument.id),
            "instrument id should match instr-<v4 uuid>: ${instrument.id}",
        )
        assertTrue(
            Regex("^custom-$uuidShape$").matches(tuning.id),
            "tuning id should match custom-<v4 uuid>: ${tuning.id}",
        )
    }

    @Test
    fun customInstrumentSelectionFallsBackToItsDefaultTuning() {
        val storage = MemoryKeyValueStorage()
        val registry = InstrumentRegistry(storage)
        val instrument = registry.createInstrument("Weird", 4, listOf(40, 45, 50, 55))

        storage.setItem(
            INSTRUMENT_REGISTRY_STORAGE_KEY,
            """{"version":1,""" +
                "\"customInstruments\":[{\"id\":\"${instrument.id}\",\"name\":\"Weird\"," +
                "\"stringCount\":4,\"defaultNotes\":[40,45,50,55]}]," +
                "\"customTunings\":[],\"selectedInstrumentId\":\"${instrument.id}\"," +
                "\"selectedTuningId\":\"standard\"}",
        )

        val reloaded = InstrumentRegistry(storage)
        assertEquals(instrument.id, reloaded.selectedInstrumentIdFlow.value)
        assertEquals("${instrument.id}-default", reloaded.selectedTuningIdFlow.value)
    }
}
