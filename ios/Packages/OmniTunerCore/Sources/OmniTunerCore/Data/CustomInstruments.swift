import Foundation

public let minStringCount = 1
public let maxStringCount = 12
public let maxCustomInstrumentNameLength = 30
public let maxCustomTuningNameLength = 40
public let minTunerMidiNote = 23
public let maxTunerMidiNote = 86

public struct InvalidInstrumentError: LocalizedError, Equatable, Sendable {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var errorDescription: String? { message }
}

public struct CustomInstrument: Sendable, Hashable, Codable, Identifiable {
    public let id: String
    public let name: String
    public let stringCount: Int
    public let defaultNotes: [Int]

    public init(id: String, name: String, stringCount: Int, defaultNotes: [Int]) {
        self.id = id
        self.name = name
        self.stringCount = stringCount
        self.defaultNotes = defaultNotes
    }
}

public struct CustomTuning: Sendable, Hashable, Codable, Identifiable {
    public let id: String
    public let instrumentId: String
    public let name: String
    public let notes: [Int]

    public init(id: String, instrumentId: String, name: String, notes: [Int]) {
        self.id = id
        self.instrumentId = instrumentId
        self.name = name
        self.notes = notes
    }
}

public func validateInstrumentName(_ name: String) throws -> String {
    let normalized = name.trimmingCharacters(in: .whitespacesAndNewlines)
    if normalized.isEmpty {
        throw InvalidInstrumentError("An instrument name is required.")
    }
    if normalized.count > maxCustomInstrumentNameLength {
        throw InvalidInstrumentError("Instrument names must be \(maxCustomInstrumentNameLength) characters or fewer.")
    }
    return normalized
}

public func validateTuningName(_ name: String) throws -> String {
    let normalized = name.trimmingCharacters(in: .whitespacesAndNewlines)
    if normalized.isEmpty {
        throw InvalidInstrumentError("A tuning name is required.")
    }
    if normalized.count > maxCustomTuningNameLength {
        throw InvalidInstrumentError("Tuning names must be \(maxCustomTuningNameLength) characters or fewer.")
    }
    return normalized
}

public func validateStringCount(_ count: Int) throws -> Int {
    if count < minStringCount || count > maxStringCount {
        throw InvalidInstrumentError("String count must be between \(minStringCount) and \(maxStringCount).")
    }
    return count
}

public func validateNotes(_ notes: [Int], expectedCount: Int) throws -> [Int] {
    let inRange = notes.allSatisfy { $0 >= minTunerMidiNote && $0 <= maxTunerMidiNote }
    if notes.count != expectedCount || !inRange {
        throw InvalidInstrumentError("Tuning requires \(expectedCount) notes from MIDI \(minTunerMidiNote) to \(maxTunerMidiNote).")
    }
    return notes
}
