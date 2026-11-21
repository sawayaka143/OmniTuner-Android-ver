import Foundation
import OmniTunerCore
import SwiftUI

public struct ScaleTone: Identifiable, Sendable {
    public var id: Int { pitchClass }
    public let pitchClass: Int
    public let midi: Int
    public let noteName: String
    public let intervalLabel: String
    public let isRoot: Bool
}

public struct ScalesUiState: Sendable {
    public let settings: ScaleSettings
    public let library: LibraryState
    public let instrument: Instrument
    public let tuning: Tuning
    public let catalog: [InstrumentTunings]
    public let board: [[FretCell]]
    public let tones: [ScaleTone]
    public let isPlaying: Bool
    public let sounding: SoundingNote?
}

@MainActor
@Observable
public final class ScalesModelView {
    private static let scaleNoteDelayMs: Int = 330
    private static let tapPulseMs: Int = 240

    private let container: AppContainer
    private var isPlaying = false
    private var sounding: SoundingNote? = nil
    private var playTask: Task<Void, Never>? = nil

    public init(container: AppContainer) {
        self.container = container
    }

    public var ui: ScalesUiState {
        let settings = container.scales.value
        let lib = container.instruments.state
        return ScalesUiState(
            settings: settings,
            library: lib,
            instrument: lib.instrument,
            tuning: lib.tuning,
            catalog: lib.catalog,
            board: computeBoard(settings: settings, tuning: lib.tuning),
            tones: computeTones(settings: settings, tuning: lib.tuning),
            isPlaying: isPlaying,
            sounding: sounding
        )
    }

    public func setScaleId(_ scaleId: String) { edit { $0.scaleId = scaleId } }
    public func setRootPitchClass(_ root: Int) { edit { $0.rootPitchClass = root } }
    public func setAccidental(_ accidental: Accidental) { edit { $0.accidental = accidental } }
    public func setFretCount(_ count: Int) { edit { $0.fretCount = count } }
    public func setLabelMode(_ mode: LabelMode) { edit { $0.labelMode = mode } }
    public func setShowOutsideScale(_ show: Bool) { edit { $0.showOutsideScale = show } }

    public func selectTuning(instrumentId: String, tuningId: String) {
        container.instruments.select(instrumentId: instrumentId, tuningId: tuningId)
    }

    public func playCell(_ cell: FretCell) {
        guard let midi = cell.midi else { return }
        pulse(SoundingNote(midi: midi, stringIndex: cell.stringIndex, fret: cell.fret))
    }

    public func playTone(midi: Int) {
        pulse(noteAt(midi: midi))
    }

    public func togglePlayScale() {
        if isPlaying {
            stopPlayback()
            return
        }
        playTask?.cancel()
        let tones = ui.tones
        var run = tones.map(\.midi)
        if let first = tones.first {
            run.append(first.midi + 12)
        }
        isPlaying = true
        playTask = Task {
            defer {
                self.isPlaying = false
                self.sounding = nil
            }
            for midi in run {
                if Task.isCancelled { return }
                self.sounding = self.noteAt(midi: midi)
                self.container.noteAudio.playNote(midi: midi)
                try? await Task.sleep(for: .milliseconds(Self.scaleNoteDelayMs))
            }
        }
    }

    public func stopPlayback() {
        playTask?.cancel()
        playTask = nil
        isPlaying = false
        sounding = nil
    }

    private func pulse(_ note: SoundingNote) {
        container.noteAudio.playNote(midi: note.midi)
        guard !isPlaying else { return }
        playTask?.cancel()
        playTask = Task {
            self.sounding = note
            try? await Task.sleep(for: .milliseconds(Self.tapPulseMs))
            if !Task.isCancelled {
                self.sounding = nil
            }
        }
    }

    private func noteAt(midi: Int) -> SoundingNote {
        let cell = ui.board.flatMap { $0 }
            .filter { $0.midi == midi && $0.interval != nil }
            .min { $0.fret < $1.fret }
        return SoundingNote(midi: midi, stringIndex: cell?.stringIndex, fret: cell?.fret)
    }

    private func edit(_ transform: (inout ScaleSettings) -> Void) {
        container.scales.update(transform)
    }

    private func computeTones(settings: ScaleSettings, tuning: Tuning) -> [ScaleTone] {
        let scale = allScales.first { $0.id == settings.scaleId } ?? allScales[0]
        let flats = settings.accidental == .flat
        let lowest = tuning.strings.compactMap { frequencyToMidiNote($0.freq) }.min() ?? 40
        let rootMidi = lowest + mod12(settings.rootPitchClass - lowest)
        return scale.intervals.map { interval in
            let midi = rootMidi + interval.semitones
            return ScaleTone(
                pitchClass: mod12(midi),
                midi: midi,
                noteName: noteName(pitchClass: mod12(midi), preferFlats: flats),
                intervalLabel: interval.label,
                isRoot: mod12(interval.semitones) == 0
            )
        }
    }

    private func computeBoard(settings: ScaleSettings, tuning: Tuning) -> [[FretCell]] {
        let midiNotes = tuning.strings.map { frequencyToMidiNote($0.freq) ?? 69 }
        let pitchClasses = tuning.strings.enumerated().map { idx, string in
            parseNote(string.name) ?? mod12(midiNotes[idx])
        }
        let scale = allScales.first { $0.id == settings.scaleId } ?? allScales[0]
        return computeFretboard(
            openPitchClasses: pitchClasses.reversed(),
            fretCount: settings.fretCount,
            intervals: scale.intervals,
            preferFlats: settings.accidental == .flat,
            openMidiNotes: midiNotes.reversed(),
            rootPitchClass: settings.rootPitchClass
        )
    }
}
