import Foundation
import OmniTunerCore
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public enum TuneStatus: Equatable, Sendable {
    case idle
    case listening
    case adjusting(String)
    case inTune
}

public struct TunerUiState: Sendable {
    public var capturing: Bool = false
    public var error: String? = nil
    public var mode: TunerMode = .auto
    public var instruments: [Instrument] = builtInInstruments
    public var instrument: Instrument = builtInInstruments[0]
    public var tunings: [Tuning] = builtInInstruments[0].tunings
    public var tuning: Tuning = builtInInstruments[0].tunings[0]
    public var catalog: [InstrumentTunings] = []
    public var activeIndex: Int? = nil
    public var targetMidi: Int? = nil
    public var cents: Double? = nil
    public var tolerance: Int = inTuneToleranceCents
    public var needlePercent: Double = 50.0
    public var noteMidi: Int? = nil
    public var frequency: Double? = nil
    public var tuned: Bool = false
    public var status: TuneStatus = .idle
    public var tunedStrings: Set<String> = []
    public var pulse: Bool = false
    public var holding: Bool = false
    public var haptics: Bool = true
    public var proAccuracy: Bool = false
    public var recent: [RecentTuning] = []
}

@MainActor
@Observable
public final class TunerModel {
    private static let lockPulseMs: Int64 = 900
    private static let noteSnapCents = 50.0
    private static let levelFloorDb = -66.0
    private static let levelCeilingDb = -24.0
    private static let levelAttack: Float = 0.5
    private static let levelRelease: Float = 0.12

    private let container: AppContainer
    private let capture = PitchCapture()
    private let lock = TuneLock()

    public private(set) var inputLevel: Float = 0.0
    public private(set) var hasPermission: Bool = PitchCapture.hasPermission()
    public private(set) var permissionDenied: Bool = false
    public private(set) var lockPulseCount: Int = 0

    private var capturing = false
    private var startingCapture = false
    private var captureGeneration: UInt64 = 0
    private var captureError: String? = nil
    private var latestFrame: PitchFrame = .silent
    private var mode: TunerMode
    private var manualIndex = 0
    private var lockConfirmed = false
    private var tunedStrings: Set<String> = []
    private var pulseUntilMs: Int64 = 0
    private var previousTarget: String? = nil
    @ObservationIgnored private var lastAppliedLowestHz: Double? = nil

    public init(container: AppContainer) {
        self.container = container
        self.mode = container.tuner.value.initialMode()
        syncLowestFrequency()
    }

    public var ui: TunerUiState {
        let settings = container.tuner.value
        let lib = container.instruments.state
        let strings = lib.tuning.strings
        let reading = readFrame(
            frame: latestFrame,
            mode: mode,
            manualIndex: manualIndex,
            strings: strings,
            referencePitch: settings.referencePitch,
            previousTarget: previousTarget,
            capturing: capturing
        )
        let tracking = latestFrame.tracking
        let cents = reading.cents
        let withinTolerance = cents != nil && abs(cents!) <= Double(inTuneToleranceCents)
        let showTuned = settings.inTuneFeedback && lockConfirmed
        let tunedNow = settings.inTuneFeedback
            ? showTuned
            : (tracking == .locked && withinTolerance)

        let status: TuneStatus
        if !capturing {
            status = .idle
        } else if showTuned {
            status = .inTune
        } else if let name = reading.targetName, tracking == .locked || tracking == .holding {
            status = .adjusting(name)
        } else {
            status = .listening
        }

        let targetMidi: Int? = (mode == .manual)
            ? reading.activeIndex.flatMap { frequencyToMidiNote(strings[$0].freq) }
            : nil

        return TunerUiState(
            capturing: capturing,
            error: captureError,
            mode: mode,
            instruments: lib.instruments,
            instrument: lib.instrument,
            tunings: lib.tunings,
            tuning: lib.tuning,
            catalog: lib.catalog,
            activeIndex: reading.activeIndex,
            targetMidi: targetMidi,
            cents: cents,
            tolerance: inTuneToleranceCents,
            needlePercent: needlePercentFromCents(cents),
            noteMidi: reading.noteMidi,
            frequency: latestFrame.frequency,
            tuned: tunedNow && reading.activeIndex != nil,
            status: status,
            tunedStrings: tunedStrings,
            pulse: pulseUntilMs > currentUptimeMs(),
            holding: tracking == .holding,
            haptics: settings.inTuneFeedback && settings.haptics,
            proAccuracy: settings.proAccuracy,
            recent: lib.recent
        )
    }

    public func ensurePermissionAndStart() async {
        hasPermission = PitchCapture.hasPermission()
        if !hasPermission {
            let granted = await PitchCapture.requestPermission()
            guard !Task.isCancelled else { return }
            hasPermission = granted
            permissionDenied = !granted
            if !granted {
                onPermissionDenied()
                return
            }
        }
        guard !Task.isCancelled else { return }
        permissionDenied = false
        startCapture()
    }

    public func startCapture() {
        guard !capturing && !startingCapture else { return }
        captureGeneration &+= 1
        let gen = captureGeneration
        startingCapture = true
        capturing = true
        captureError = nil
        syncLowestFrequency()
        capture.startAsync(
            onFrame: { [weak self] frame in
                Task { @MainActor [weak self] in
                    self?.handleCapturedFrame(frame)
                }
            },
            onStarted: { [weak self] error in
                Task { @MainActor [weak self] in
                    guard let self, self.startingCapture, self.captureGeneration == gen else { return }
                    self.startingCapture = false
                    if let error {
                        self.capturing = false
                        self.captureError = error
                    }
                    #if canImport(UIKit)
                    UIApplication.shared.isIdleTimerDisabled = self.capturing
                    #endif
                }
            }
        )
    }

    public func stopCapture() {
        guard capturing || startingCapture else { return }
        captureGeneration &+= 1
        startingCapture = false
        capturing = false
        capture.stopAsync()
        inputLevel = 0
        latestFrame = .silent
        #if canImport(UIKit)
        UIApplication.shared.isIdleTimerDisabled = false
        #endif
    }

    public func onPermissionDenied() {
        stopCapture()
        captureError = "Microphone access is unavailable. Check permissions in Settings and try again."
    }

    public func selectMode(_ newMode: TunerMode) {
        guard mode != newMode else { return }
        if newMode == .manual {
            let reading = readFrame(
                frame: latestFrame,
                mode: .auto,
                manualIndex: manualIndex,
                strings: container.instruments.state.tuning.strings,
                referencePitch: container.tuner.value.referencePitch,
                previousTarget: previousTarget,
                capturing: capturing
            )
            if let idx = reading.activeIndex {
                manualIndex = idx
            }
        }
        mode = newMode
        resetHold()
        container.tuner.update { $0.mode = newMode }
    }

    public func selectString(index: Int) {
        manualIndex = index
        mode = .manual
        resetHold()
        container.tuner.update { $0.mode = .manual }
        let strings = container.instruments.state.tuning.strings
        if index >= 0 && index < strings.count {
            container.noteAudio.playNote(midi: frequencyToMidiNote(strings[index].freq) ?? 69)
        }
    }

    public func selectTuning(instrumentId: String, tuningId: String) {
        let current = container.instruments.state
        if current.instrument.id == instrumentId && current.tuning.id == tuningId { return }
        resetHold()
        container.instruments.select(instrumentId: instrumentId, tuningId: tuningId)
        syncLowestFrequency()
    }

    public func saveCustomTuning(editingId: String?, name: String, notes: [Int]) -> String? {
        let instrument = container.instruments.state.instrument
        do {
            let validName = try validateTuningName(name)
            let validNotes = try validateNotes(notes, expectedCount: instrument.stringCount)
            container.instruments.saveTuning(editingId: editingId, instrumentId: instrument.id, name: validName, notes: validNotes)
            resetHold()
            syncLowestFrequency()
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    public func deleteCustomTuning(_ tuningId: String) {
        resetHold()
        container.instruments.deleteTuning(tuningId)
        syncLowestFrequency()
    }

    public func saveCustomInstrument(editingId: String?, name: String, stringCount: Int, notes: [Int]) -> String? {
        do {
            let validName = try validateInstrumentName(name)
            let validCount = try validateStringCount(stringCount)
            let validNotes = try validateNotes(notes, expectedCount: validCount)
            container.instruments.saveInstrument(editingId: editingId, name: validName, stringCount: validCount, notes: validNotes)
            resetHold()
            syncLowestFrequency()
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    public func deleteCustomInstrument(_ instrumentId: String) {
        resetHold()
        container.instruments.deleteInstrument(instrumentId)
        syncLowestFrequency()
    }

    private func handleCapturedFrame(_ frame: PitchFrame) {
        latestFrame = frame
        inputLevel = smoothedLevel(previous: inputLevel, rms: frame.inputLevel)

        let settings = container.tuner.value
        let strings = container.instruments.state.tuning.strings
        let reading = readFrame(
            frame: frame,
            mode: mode,
            manualIndex: manualIndex,
            strings: strings,
            referencePitch: settings.referencePitch,
            previousTarget: previousTarget,
            capturing: capturing
        )
        if mode == .auto && frame.tracking == .locked, let targetName = reading.targetName {
            previousTarget = targetName
        }

        let now = currentUptimeMs()
        let justConfirmed = lock.step(
            now: now,
            cents: reading.cents,
            tracking: frame.tracking,
            tolerance: Double(inTuneToleranceCents),
            holdMs: inTuneHoldMs
        )
        if justConfirmed {
            onConfirmed(reading: reading, settings: settings, currentMode: mode, now: now)
        }
        lockConfirmed = lock.confirmed
    }

    private func onConfirmed(reading: Reading, settings: TunerSettings, currentMode: TunerMode, now: Int64) {
        lockPulseCount += 1
        if currentMode == .auto, let name = reading.targetName {
            tunedStrings.insert(name)
        }
        if settings.inTuneFeedback {
            pulseUntilMs = now + Self.lockPulseMs
            if settings.chime {
                container.noteAudio.playChime()
            }
            if settings.haptics {
                #if canImport(UIKit)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                #endif
            }
        }
    }

    private func resetHold() {
        lock.reset()
        previousTarget = nil
        lockConfirmed = false
        tunedStrings = []
        pulseUntilMs = 0
    }

    private func syncLowestFrequency() {
        let lowest = container.instruments.state.tuning.strings.map(\.freq).min()
        if lowest != lastAppliedLowestHz {
            lastAppliedLowestHz = lowest
            capture.setLowestFrequency(lowest)
        }
    }

    private struct Reading {
        let activeIndex: Int?
        let cents: Double?
        let noteMidi: Int?
        let targetName: String?
    }

    private func readFrame(
        frame: PitchFrame,
        mode: TunerMode,
        manualIndex: Int,
        strings: [NamedFrequency],
        referencePitch: Int,
        previousTarget: String?,
        capturing: Bool
    ) -> Reading {
        let played = frame.frequency.flatMap { frequencyToMidiFloat($0, ref: Double(referencePitch)) }

        if mode == .manual {
            let index: Int? = strings.isEmpty ? nil : min(manualIndex, strings.count - 1)
            let targetMidi = index.flatMap { frequencyToMidiNote(strings[$0].freq) ?? 69 }
            let cents = (played != nil && targetMidi != nil) ? centsFromMidiFloat(played, targetMidi: targetMidi!) : nil
            let noteMidi: Int? = if let targetMidi, let cents, abs(cents) < Self.noteSnapCents {
                targetMidi
            } else {
                nearestSemitone(played)
            }
            return Reading(activeIndex: index, cents: cents, noteMidi: noteMidi, targetName: index.map { strings[$0].name })
        }

        let target = (capturing && played != nil)
            ? nearestStringTarget(playedMidiFloat: played, strings: strings, previousName: previousTarget)
            : nil
        let cents = target?.cents
        let noteMidi: Int? = if let target, let cents, abs(cents) < Self.noteSnapCents {
            target.midi
        } else {
            nearestSemitone(played)
        }
        let index = target.flatMap { found in strings.firstIndex { $0.name == found.name } }
        return Reading(activeIndex: index, cents: cents, noteMidi: noteMidi, targetName: target?.name)
    }

    private func smoothedLevel(previous: Float, rms: Double) -> Float {
        let db = rms > 0 ? 20.0 * log10(rms) : Self.levelFloorDb
        let target = Float(min(1.0, max(0.0, (db - Self.levelFloorDb) / (Self.levelCeilingDb - Self.levelFloorDb))))
        let rate = target > previous ? Self.levelAttack : Self.levelRelease
        return previous + (target - previous) * rate
    }

    private func currentUptimeMs() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000.0)
    }
}
