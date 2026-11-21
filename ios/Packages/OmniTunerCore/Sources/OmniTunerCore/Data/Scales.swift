public struct ScaleInterval: Sendable, Hashable {
    public let semitones: Int
    public let label: String

    public init(semitones: Int, label: String) {
        self.semitones = semitones
        self.label = label
    }
}

public struct Scale: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String
    public let group: String
    public let intervals: [ScaleInterval]

    public init(id: String, label: String, group: String, intervals: [ScaleInterval]) {
        self.id = id
        self.label = label
        self.group = group
        self.intervals = intervals
    }
}

public let sharpNames: [String] = [
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
]

public let flatNames: [String] = [
    "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
]

public let allScales: [Scale] = [
    Scale(
        id: "major",
        label: "Major",
        group: "Major & minor",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 4, label: "3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
            ScaleInterval(semitones: 11, label: "7"),
        ]
    ),
    Scale(
        id: "natural-minor",
        label: "Minor",
        group: "Major & minor",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 8, label: "♭6"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "harmonic-minor",
        label: "Harmonic minor",
        group: "Major & minor",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 8, label: "♭6"),
            ScaleInterval(semitones: 11, label: "7"),
        ]
    ),
    Scale(
        id: "melodic-minor",
        label: "Melodic minor",
        group: "Major & minor",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
            ScaleInterval(semitones: 11, label: "7"),
        ]
    ),
    Scale(
        id: "dorian",
        label: "Dorian",
        group: "Modes",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "phrygian",
        label: "Phrygian",
        group: "Modes",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 1, label: "♭2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 8, label: "♭6"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "lydian",
        label: "Lydian",
        group: "Modes",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 4, label: "3"),
            ScaleInterval(semitones: 6, label: "♯4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
            ScaleInterval(semitones: 11, label: "7"),
        ]
    ),
    Scale(
        id: "mixolydian",
        label: "Mixolydian",
        group: "Modes",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 4, label: "3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "locrian",
        label: "Locrian",
        group: "Modes",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 1, label: "♭2"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 6, label: "♭5"),
            ScaleInterval(semitones: 8, label: "♭6"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "major-pentatonic",
        label: "Major pentatonic",
        group: "Pentatonic & blues",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 2, label: "2"),
            ScaleInterval(semitones: 4, label: "3"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 9, label: "6"),
        ]
    ),
    Scale(
        id: "minor-pentatonic",
        label: "Minor pentatonic",
        group: "Pentatonic & blues",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
    Scale(
        id: "blues",
        label: "Blues",
        group: "Pentatonic & blues",
        intervals: [
            ScaleInterval(semitones: 0, label: "1"),
            ScaleInterval(semitones: 3, label: "♭3"),
            ScaleInterval(semitones: 5, label: "4"),
            ScaleInterval(semitones: 6, label: "♭5"),
            ScaleInterval(semitones: 7, label: "5"),
            ScaleInterval(semitones: 10, label: "♭7"),
        ]
    ),
]
