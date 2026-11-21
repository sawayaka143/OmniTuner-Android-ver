import Foundation
import OmniTunerCore
import SwiftUI

public enum ChordsTab: String, CaseIterable, Sendable {
    case voicings, keyFinder
}

public struct ChordEntry: Identifiable, Sendable {
    public let id: Int
    public let token: String
    public let chord: ParsedChord?
    public let error: String?
    public let shapes: [VoicingShape]
    public let badge: DiatonicBadge?
}

public struct GeneratedVoicings: Sendable {
    public let tuning: ParsedTuning
    public let chords: [ChordEntry]
    public let key: DetectedKey?
}

public struct ParseCount: Sendable {
    public let total: Int
    public let readable: Int
}

public struct KeyAnalysis: Sendable {
    public let count: ParseCount
    public let key: DetectedKey?
}

public struct TabRow: Identifiable, Sendable {
    public let id: Int
    public let label: String
    public let padLeft: Int
    public let symbol: String
    public let padRight: Int
    public let muted: Bool
}

public struct ChordFinderUiState: Sendable {
    public let tab: ChordsTab
    public let library: LibraryState
    public let settings: ChordSettings
    public let progressionInput: String
    public let progressionCount: ParseCount
    public let tonicPc: Int
    public let generating: Bool
    public let results: GeneratedVoicings?
    public let keyInput: String
    public let keyAnalysis: KeyAnalysis
}

@MainActor
@Observable
public final class ChordFinderModel {
    private static let chordDurationSeconds: Double = 1.2
    private static let progressionGapMs: UInt64 = 700
    private static let voicingNoteGapMs: UInt64 = 90
    private static let minShuffleChords = 3
    private static let defaultProgression = "C, G, Am, F"

    private let container: AppContainer
    private var tab: ChordsTab = .voicings
    private var progressionInput: String = defaultProgression
    private var tonicPc: Int = 0
    private var keyInput: String = ""
    private var generating = false
    private var rawResult: GeneratedVoicings? = nil

    private var generateTask: Task<Void, Never>? = nil
    private var playTask: Task<Void, Never>? = nil
    private var lastTuningId: String = ""

    public init(container: AppContainer) {
        self.container = container
        self.lastTuningId = "\(container.instruments.state.instrument.id)|\(container.instruments.state.tuning.id)"
        generate()
    }

    public var ui: ChordFinderUiState {
        let lib = container.instruments.state
        let settings = container.chords.value
        let slicedResult = rawResult.map { res in
            GeneratedVoicings(
                tuning: res.tuning,
                chords: res.chords.map { entry in
                    ChordEntry(
                        id: entry.id,
                        token: entry.token,
                        chord: entry.chord,
                        error: entry.error,
                        shapes: Array(entry.shapes.prefix(settings.voicingCount)),
                        badge: entry.badge
                    )
                },
                key: res.key
            )
        }
        return ChordFinderUiState(
            tab: tab,
            library: lib,
            settings: settings,
            progressionInput: progressionInput,
            progressionCount: countReadable(progressionInput, tonicPc: tonicPc),
            tonicPc: tonicPc,
            generating: generating,
            results: slicedResult,
            keyInput: keyInput,
            keyAnalysis: analyseKey(keyInput)
        )
    }

    public func syncTuningIfChanged() {
        let currentKey = "\(container.instruments.state.instrument.id)|\(container.instruments.state.tuning.id)"
        if currentKey != lastTuningId {
            lastTuningId = currentKey
            if rawResult != nil {
                generate()
            }
        }
    }

    public func selectTab(_ newTab: ChordsTab) { tab = newTab }
    public func setProgressionInput(_ text: String) { progressionInput = text }
    public func setTonic(_ pc: Int) { tonicPc = pc }
    public func setKeyInput(_ text: String) { keyInput = text }

    public func selectTuning(instrumentId: String, tuningId: String) {
        container.instruments.select(instrumentId: instrumentId, tuningId: tuningId)
        lastTuningId = "\(instrumentId)|\(tuningId)"
        if rawResult != nil { generate() }
    }

    public func changeVoicingCount(_ delta: Int) {
        container.chords.update {
            $0.voicingCount = min(maxVoicingCount, max(minVoicingCount, $0.voicingCount + delta))
        }
    }

    public func setView(_ view: VoicingViewMode) {
        container.chords.update { $0.view = view }
    }

    public func setLabels(_ labels: DiagramLabels) {
        container.chords.update { $0.labels = labels }
    }

    public func shuffle() {
        guard let preset = progressionPresets.randomElement() else { return }
        let tonic = Int.random(in: 0..<12)
        let chords = degreesToProgression(preset.degrees, tonicPc: tonic, useFlats: flatsForPc(tonic))
        guard chords.count >= Self.minShuffleChords else { return }
        progressionInput = chords.joined(separator: ", ")
        tonicPc = tonic
        generate()
    }

    public func clear() {
        generateTask?.cancel()
        generating = false
        rawResult = nil
    }

    public func generate() {
        guard let tuning = parsedTuningOf(container.instruments.state.tuning) else { return }
        let tokens = tokenizeProgression(progressionInput)
        guard !tokens.isEmpty else { return }
        let currentTonic = tonicPc

        generateTask?.cancel()
        generating = true
        generateTask = Task {
            let result = await Task.detached(priority: .userInitiated) {
                Self.voice(tokens: tokens, tonicPc: currentTonic, tuning: tuning)
            }.value
            guard !Task.isCancelled else { return }
            self.rawResult = result
            self.generating = false
        }
    }

    public func playShape(_ shape: VoicingShape) {
        playTask?.cancel()
        playTask = Task {
            for note in shape.sounding {
                if Task.isCancelled { return }
                container.noteAudio.playNote(midi: note.midi)
                try? await Task.sleep(for: .milliseconds(Self.voicingNoteGapMs))
            }
        }
    }

    public func playProgression() {
        let shapes = ui.results?.chords.compactMap { $0.shapes.first } ?? []
        guard !shapes.isEmpty else { return }
        playTask?.cancel()
        playTask = Task {
            for shape in shapes {
                if Task.isCancelled { return }
                for note in shape.sounding {
                    container.noteAudio.playNote(midi: note.midi, durationSeconds: Self.chordDurationSeconds)
                }
                try? await Task.sleep(for: .milliseconds(Self.progressionGapMs))
            }
        }
    }

    public func tabText() -> String? {
        guard let result = ui.results else { return nil }
        let tuning = result.tuning
        var lines: [String] = []
        let key = result.key.map { keyName(tonicName: $0.tonicName, mode: $0.mode) } ?? "—"
        lines.append("Chord finder — tuning \(tuning.labels.joined(separator: " ")) — key \(key)")
        lines.append("")
        for entry in result.chords {
            guard let chord = entry.chord else {
                lines.append("=== \(entry.token) ===")
                lines.append("!! \(entry.error ?? "invalid")")
                lines.append("")
                continue
            }
            for (k, shape) in entry.shapes.enumerated() {
                lines.append("=== \(chord.symbol) - Fingering \(k + 1) ===")
                for row in Self.tabRows(shape: shape, tuning: tuning) {
                    let left = String(repeating: "-", count: row.padLeft)
                    let right = String(repeating: "-", count: row.padRight)
                    lines.append("\(row.label)|\(left)\(row.symbol)\(right)|")
                }
                lines.append("")
            }
        }
        return lines.joined(separator: "\n")
    }

    public static func chordTonesLabel(chord: ParsedChord, flats: Bool) -> String {
        let optional = Set(chord.optionalPcs)
        let tones = chord.pcs.map { pc -> String in
            let name = pcName(pc, flats: flats)
            return optional.contains(pc) ? "(\(name))" : name
        }.joined(separator: " ")
        return "\(chord.quality) · \(tones)"
    }

    public static func tabRows(shape: VoicingShape, tuning: ParsedTuning) -> [TabRow] {
        let width = shape.frets.contains { ($0 ?? 0) >= 10 } ? 8 : 7
        return tuning.midi.indices.reversed().map { s in
            let fret = shape.frets[s]
            let symbol = fret.map(String.init) ?? "x"
            let padLeft = (width - symbol.lengthOfBytes(using: .utf8)) / 2
            let paddedLabel = tuning.labels[s].padding(toLength: 5, withPad: " ", startingAt: 0)
            return TabRow(
                id: s,
                label: paddedLabel,
                padLeft: padLeft,
                symbol: symbol,
                padRight: max(0, width - symbol.count - padLeft),
                muted: fret == nil
            )
        }
    }

    private nonisolated static func voice(tokens: [String], tonicPc: Int, tuning: ParsedTuning) -> GeneratedVoicings {
        let parses = tokens.map { resolveToken($0, tonicPc: tonicPc) }
        let chords: [ParsedChord] = parses.compactMap {
            if case .ok(let chord) = $0 { return chord }
            return nil
        }
        let key = chords.isEmpty ? nil : detectKey(chords, tuningFlats: tuning.flats)
        var entries: [ChordEntry] = []
        for (idx, (token, parse)) in zip(tokens, parses).enumerated() {
            if Task.isCancelled { break }
            switch parse {
            case .error(_, let error):
                entries.append(ChordEntry(id: idx, token: token, chord: nil, error: error, shapes: [], badge: nil))
            case .ok(let chord):
                let badge: DiatonicBadge? = key.map { detected in
                    let useFlats = chords.contains(where: \.flats) || tuning.flats || flatsForPc(detected.tonicPc)
                    return computeBadgeForPc(
                        chord: chord,
                        tonicPc: detected.tonicPc,
                        modeName: detected.mode,
                        tuningFlats: tuning.flats,
                        tonicFlats: useFlats
                    )
                }
                let shapes = searchChord(tuning: tuning, chord: chord, count: maxVoicingCount)
                entries.append(ChordEntry(id: idx, token: token, chord: chord, error: nil, shapes: shapes, badge: badge))
            }
        }
        return GeneratedVoicings(tuning: tuning, chords: entries, key: key)
    }

    private nonisolated static func resolveToken(_ token: String, tonicPc: Int) -> ChordParseResult {
        let direct = parseChord(token)
        if case .ok = direct { return direct }
        let flats = flatsForPc(tonicPc) || token.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("b")
        guard let symbol = degreeToChordSymbol(token, tonicPc: tonicPc, useFlats: flats) else { return direct }
        let parsed = parseChord(symbol)
        if case .ok = parsed { return parsed }
        return direct
    }

    private func countReadable(_ text: String, tonicPc: Int) -> ParseCount {
        let tokens = tokenizeProgression(text)
        let ok = tokens.filter {
            if case .ok = Self.resolveToken($0, tonicPc: tonicPc) { return true }
            return false
        }.count
        return ParseCount(total: tokens.count, readable: ok)
    }

    private func analyseKey(_ text: String) -> KeyAnalysis {
        let tokens = tokenizeProgression(text)
        let chords: [ParsedChord] = tokens.compactMap {
            if case .ok(let chord) = Self.resolveToken($0, tonicPc: 0) { return chord }
            return nil
        }
        return KeyAnalysis(
            count: ParseCount(total: tokens.count, readable: chords.count),
            key: chords.isEmpty ? nil : detectKey(chords)
        )
    }

    private func parsedTuningOf(_ tuning: Tuning) -> ParsedTuning? {
        let tokens = tuning.strings.compactMap { string -> String? in
            guard let midi = frequencyToMidiNote(string.freq) else { return nil }
            return midiName(midi, flats: false)
        }
        guard tokens.count == tuning.strings.count else { return nil }
        if case .ok(let parsed) = parseTuning(tokens.joined(separator: " ")) {
            return parsed
        }
        return nil
    }
}
