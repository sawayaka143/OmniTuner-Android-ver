import Foundation

public struct FretCell: Sendable, Hashable, Identifiable {
    public var id: String { "\(stringIndex)-\(fret)" }
    public let stringIndex: Int
    public let fret: Int
    public let pitchClass: Int
    public let midi: Int?
    public let interval: ScaleInterval?
    public let noteName: String
    public let isRoot: Bool
}

private func normalizeNote(_ input: String) -> String {
    let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "♭", with: "b")
        .replacingOccurrences(of: "♯", with: "#")
    guard let first = trimmed.first else { return trimmed }
    return first.uppercased() + String(trimmed.dropFirst())
}

private let sharpIndex: [String: Int] = Dictionary(
    uniqueKeysWithValues: sharpNames.enumerated().map { ($0.element, $0.offset) }
)

private let flatIndex: [String: Int] = Dictionary(
    uniqueKeysWithValues: flatNames.enumerated().map { ($0.element, $0.offset) }
)

private let trailingOctaveRegex = try! NSRegularExpression(pattern: "[0-9].*$")

public func parseNote(_ input: String) -> Int? {
    guard !input.isEmpty else { return nil }
    let normalized = normalizeNote(input)
    let range = NSRange(normalized.startIndex..<normalized.endIndex, in: normalized)
    let withoutOctave = trailingOctaveRegex.stringByReplacingMatches(in: normalized, range: range, withTemplate: "")
    return sharpIndex[withoutOctave] ?? flatIndex[withoutOctave]
}

public func noteName(pitchClass: Int, preferFlats: Bool) -> String {
    let index = ((pitchClass % 12) + 12) % 12
    return (preferFlats ? flatNames : sharpNames)[index]
}

public func intervalByPitchClass(_ intervals: [ScaleInterval]) -> [Int: ScaleInterval] {
    var map: [Int: ScaleInterval] = [:]
    for interval in intervals {
        let pc = ((interval.semitones % 12) + 12) % 12
        map[pc] = interval
    }
    return map
}

public func computeFretboard(
    openPitchClasses: [Int],
    fretCount: Int,
    intervals: [ScaleInterval],
    preferFlats: Bool,
    openMidiNotes: [Int]? = nil,
    rootPitchClass: Int = 0
) -> [[FretCell]] {
    let intervalMap = intervalByPitchClass(intervals)
    var board: [[FretCell]] = []
    board.reserveCapacity(openPitchClasses.count)

    for stringIndex in openPitchClasses.indices {
        let openPc = ((openPitchClasses[stringIndex] % 12) + 12) % 12
        var row: [FretCell] = []
        row.reserveCapacity(fretCount + 1)
        for fret in 0...fretCount {
            let pitchClass = (((openPc + fret) % 12) + 12) % 12
            let interval = intervalMap[(((pitchClass - rootPitchClass) % 12) + 12) % 12]
            let openMidi = (openMidiNotes != nil && stringIndex < openMidiNotes!.count) ? openMidiNotes![stringIndex] : nil
            row.append(
                FretCell(
                    stringIndex: stringIndex,
                    fret: fret,
                    pitchClass: pitchClass,
                    midi: openMidi.map { $0 + fret },
                    interval: interval,
                    noteName: noteName(pitchClass: pitchClass, preferFlats: preferFlats),
                    isRoot: interval?.label == "R" || interval?.label == "1"
                )
            )
        }
        board.append(row)
    }
    return board
}
