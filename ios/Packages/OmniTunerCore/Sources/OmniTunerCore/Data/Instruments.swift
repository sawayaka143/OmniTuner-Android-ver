public struct Tuning: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let strings: [NamedFrequency]
    public let isCustom: Bool

    public init(id: String, label: String, strings: [NamedFrequency], isCustom: Bool = false) {
        self.id = id
        self.label = label
        self.strings = strings
        self.isCustom = isCustom
    }
}

public struct Instrument: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let stringCount: Int
    public let tunings: [Tuning]
    public let isCustom: Bool

    public init(id: String, label: String, stringCount: Int, tunings: [Tuning], isCustom: Bool = false) {
        self.id = id
        self.label = label
        self.stringCount = stringCount
        self.tunings = tunings
        self.isCustom = isCustom
    }
}

public let builtInInstruments: [Instrument] = [
    Instrument(
        id: "guitar",
        label: "Guitar",
        stringCount: 6,
        tunings: [
            Tuning(
                id: "standard",
                label: "Standard",
                strings: [
                    NamedFrequency(name: "E2", freq: 82.41),
                    NamedFrequency(name: "A2", freq: 110.0),
                    NamedFrequency(name: "D3", freq: 146.83),
                    NamedFrequency(name: "G3", freq: 196.0),
                    NamedFrequency(name: "B3", freq: 246.94),
                    NamedFrequency(name: "E4", freq: 329.63),
                ]
            ),
            Tuning(
                id: "dadgad",
                label: "DADGAD",
                strings: [
                    NamedFrequency(name: "D2", freq: 73.42),
                    NamedFrequency(name: "A2", freq: 110.0),
                    NamedFrequency(name: "D3", freq: 146.83),
                    NamedFrequency(name: "G3", freq: 196.0),
                    NamedFrequency(name: "A3", freq: 220.0),
                    NamedFrequency(name: "D4", freq: 293.66),
                ]
            ),
            Tuning(
                id: "eb",
                label: "E♭ Standard",
                strings: [
                    NamedFrequency(name: "E♭2", freq: 77.78),
                    NamedFrequency(name: "A♭2", freq: 103.83),
                    NamedFrequency(name: "D♭3", freq: 138.59),
                    NamedFrequency(name: "G♭3", freq: 185.0),
                    NamedFrequency(name: "B♭3", freq: 233.08),
                    NamedFrequency(name: "E♭4", freq: 311.13),
                ]
            ),
            Tuning(
                id: "facgce",
                label: "FACGCE",
                strings: [
                    NamedFrequency(name: "F2", freq: 87.31),
                    NamedFrequency(name: "A2", freq: 110.0),
                    NamedFrequency(name: "C3", freq: 130.81),
                    NamedFrequency(name: "G3", freq: 196.0),
                    NamedFrequency(name: "C4", freq: 261.63),
                    NamedFrequency(name: "E4", freq: 329.63),
                ]
            ),
        ]
    ),
    Instrument(
        id: "ukulele",
        label: "Ukulele",
        stringCount: 4,
        tunings: [
            Tuning(
                id: "standard",
                label: "Standard",
                strings: [
                    NamedFrequency(name: "G4", freq: 392.0),
                    NamedFrequency(name: "C4", freq: 261.63),
                    NamedFrequency(name: "E4", freq: 329.63),
                    NamedFrequency(name: "A4", freq: 440.0),
                ]
            ),
        ]
    ),
]
