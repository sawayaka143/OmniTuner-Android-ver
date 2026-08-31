package com.omnituner.core.data

data class Tuning(
    val id: String,
    val label: String,
    val strings: List<NamedFrequency>,
    val kind: String? = null,
)

data class Instrument(
    val id: String,
    val label: String,
    val stringCount: Int,
    val tunings: List<Tuning>,
    val kind: String? = null,
)

val INSTRUMENTS: List<Instrument> = listOf(
    Instrument(
        id = "guitar",
        label = "Guitar",
        stringCount = 6,
        tunings = listOf(
            Tuning(
                id = "standard",
                label = "E STANDARD",
                strings = listOf(
                    NamedFrequency("E2", 82.41),
                    NamedFrequency("A2", 110.0),
                    NamedFrequency("D3", 146.83),
                    NamedFrequency("G3", 196.0),
                    NamedFrequency("B3", 246.94),
                    NamedFrequency("E4", 329.63),
                ),
            ),
            Tuning(
                id = "dadgad",
                label = "DADGAD",
                strings = listOf(
                    NamedFrequency("D2", 73.42),
                    NamedFrequency("A2", 110.0),
                    NamedFrequency("D3", 146.83),
                    NamedFrequency("G3", 196.0),
                    NamedFrequency("A3", 220.0),
                    NamedFrequency("D4", 293.66),
                ),
            ),
            Tuning(
                id = "eb",
                label = "E♭ STANDARD",
                strings = listOf(
                    NamedFrequency("E♭2", 77.78),
                    NamedFrequency("A♭2", 103.83),
                    NamedFrequency("D♭3", 138.59),
                    NamedFrequency("G♭3", 185.0),
                    NamedFrequency("B♭3", 233.08),
                    NamedFrequency("E♭4", 311.13),
                ),
            ),
            Tuning(
                id = "facgce",
                label = "FACGCE",
                strings = listOf(
                    NamedFrequency("F2", 87.31),
                    NamedFrequency("A2", 110.0),
                    NamedFrequency("C3", 130.81),
                    NamedFrequency("G3", 196.0),
                    NamedFrequency("C4", 261.63),
                    NamedFrequency("E4", 329.63),
                ),
            ),
        ),
    ),
    Instrument(
        id = "ukulele",
        label = "Ukulele",
        stringCount = 4,
        tunings = listOf(
            Tuning(
                id = "standard",
                label = "STANDARD",
                strings = listOf(
                    NamedFrequency("G4", 392.0),
                    NamedFrequency("C4", 261.63),
                    NamedFrequency("E4", 329.63),
                    NamedFrequency("A4", 440.0),
                ),
            ),
        ),
    ),
)
