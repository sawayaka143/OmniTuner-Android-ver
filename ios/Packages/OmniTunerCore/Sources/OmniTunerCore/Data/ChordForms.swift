public struct ChordForm: Sendable, Hashable, Identifiable {
    public let id: String
    public let qualities: [String]
    public let anchor: Int
    public let offsets: [Int?]

    public init(id: String, qualities: [String], anchor: Int, offsets: [Int?]) {
        self.id = id
        self.qualities = qualities
        self.anchor = anchor
        self.offsets = offsets
    }
}

public let chordForms: [ChordForm] = [
    ChordForm(id: "maj-e", qualities: ["maj"], anchor: 0, offsets: [0, 2, 2, 1, 0, 0]),
    ChordForm(id: "maj-a", qualities: ["maj"], anchor: 1, offsets: [nil, 0, 2, 2, 2, 0]),
    ChordForm(id: "min-em", qualities: ["min"], anchor: 0, offsets: [0, 2, 2, 0, 0, 0]),
    ChordForm(id: "min-am", qualities: ["min"], anchor: 1, offsets: [nil, 0, 2, 2, 1, 0]),
    ChordForm(id: "7-e", qualities: ["7"], anchor: 0, offsets: [0, 2, 0, 1, 0, 0]),
    ChordForm(id: "7-a", qualities: ["7"], anchor: 1, offsets: [nil, 0, 2, 0, 2, 0]),
    ChordForm(id: "maj7-e", qualities: ["maj7"], anchor: 0, offsets: [0, nil, 1, 1, 0, nil]),
    ChordForm(id: "maj7-a", qualities: ["maj7"], anchor: 1, offsets: [nil, 0, 2, 1, 2, 0]),
    ChordForm(id: "m7-em", qualities: ["m7"], anchor: 0, offsets: [0, 2, 0, 0, 0, 0]),
    ChordForm(id: "m7-a", qualities: ["m7"], anchor: 1, offsets: [nil, 0, 2, 0, 1, 0]),
    ChordForm(id: "m7b5-a", qualities: ["m7b5"], anchor: 1, offsets: [nil, 0, 1, 0, 1, nil]),
    ChordForm(id: "dim7-d", qualities: ["dim7"], anchor: 2, offsets: [nil, nil, 0, 1, 0, 1]),
    ChordForm(id: "13-e", qualities: ["13"], anchor: 0, offsets: [0, nil, 0, 1, 2, nil]),
    ChordForm(id: "7b13-e", qualities: ["7b13"], anchor: 0, offsets: [0, nil, 0, 1, 1, nil]),
]
