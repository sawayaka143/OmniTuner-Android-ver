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

public struct TunerLiveState: Equatable, Sendable {
    public var capturing: Bool = false
    public var cents: Double? = nil
    public var displayCents: Double? = nil
    public var needlePercent: Double = 50.0
    public var noteMidi: Int? = nil
    public var targetMidi: Int? = nil
    public var frequency: Double? = nil
    public var tuned: Bool = false
    public var status: TuneStatus = .idle
    public var holding: Bool = false
    public var pulse: Bool = false
    public var tolerance: Int = inTuneToleranceCents
}

public struct TunerChromeState: Sendable {
    public var capturing: Bool = false
    public var error: String? = nil
    public var mode: TunerMode = .auto
    public var instruments: [Instrument] = builtInInstruments
    public var instrument: Instrument = builtInInstruments[0]
    public var tunings: [Tuning] = builtInInstruments[0].tunings
    public var tuning: Tuning = builtInInstruments[0].tunings[0]
    public var catalog: [InstrumentTunings] = []
    public var recent: [RecentTuning] = []
}

@MainActor
@Observable
public final class TunerModel {
    private static let lockPulseMs: Int64 = 900
    private static let noteSnapCents = 50.0
    private static let numericUpdateIntervalMs: Int64 = 130
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
    public private(set) var activeIndex: Int? = nil
    public private(set) var tunedStrings: Set<String> = []
    public private(set) var live: TunerLiveState = TunerLiveState()

    private var capturing = false
    private var captureError: String? = nil
    private var mode: TunerMode

    @ObservationIgnored private var startingCapture = false
    @ObservationIgnored private var captureGeneration: UInt64 = 0
    @ObservationIgnored private var latestFrame: PitchFrame = .silent
    @ObservationIgnored private var manualIndex = 0
    @ObservationIgnored private var lockConfirmed = false
    @ObservationIgnored private var previousTarget: String? = nil
    @ObservationIgnored private var lastAppliedLowestHz: Double? = nil
    @ObservationIgnored private var pulseActive = false
    @ObservationIgnored private var pulseTask: Task<Void, Never>? = nil
    @ObservationIgnored private var throttledDisplayCents: Double? = nil
    @ObservationIgnored private var lastNumericUpdateMs: Int64 = 0
    @ObservationIgnored private var lastDisplayNoteMidi: Int? = nil
    @ObservationIgnored private var lastDisplayTargetName: String? = nil
    @ObservationIgnored private var wasTunedForDisplay = false

    public init(container: AppContainer) {
        self.container = container
        self.mode = .auto
        syncLowestFrequency()
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    public var chrome: TunerChromeState {
        let lib = container.instruments.state
        return TunerChromeState(
            capturing: capturing,
            error: captureError,
            mode: mode,
            instruments: lib.instruments,
            instrument: lib.instrument,
            tunings: lib.tunings,
            tuning: lib.tuning,
            catalog: lib.catalog,
            recent: lib.recent
        )
    }

    public var hapticsEnabled: Bool {
        let settings = container.tuner.value
        return settings.inTuneFeedback && settings.haptics
    }

    public var proAccuracyEnabled: Bool {
        container.tuner.value.proAccuracy
    }

    public var inTuneFeedbackEnabled: Bool {
        container.tuner.value.inTuneFeedback
    }

    public var settingsToken: TunerSettings {
        container.tuner.value
    }

    public func refreshFromSettings() {
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
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
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
        capture.startAsync(
            onFrame: { [weak self] frame in
                DispatchQueue.main.async {
                    MainActor.assumeIsolated {
                        self?.handleCapturedFrame(frame)
                    }
                }
            },
            onStarted: { [weak self] error in
                DispatchQueue.main.async {
                    MainActor.assumeIsolated {
                        guard let self, self.startingCapture, self.captureGeneration == gen else { return }
                        self.startingCapture = false
                        if let error {
                            self.capturing = false
                            self.captureError = error
                            self.rebuildLiveStateFromLatest(now: self.currentUptimeMs(), allowNumericUpdate: true)
                        }
                        #if canImport(UIKit)
                        UIApplication.shared.isIdleTimerDisabled = self.capturing
                        #endif
                    }
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
        pulseTask?.cancel()
        pulseTask = nil
        pulseActive = false
        resetDisplaySmoothing()
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
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
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    public func selectString(index: Int) {
        manualIndex = index
        mode = .manual
        resetHold()
        let strings = container.instruments.state.tuning.strings
        if index >= 0 && index < strings.count {
            container.noteAudio.playNote(midi: frequencyToMidiNote(strings[index].freq) ?? 69)
        }
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    public func selectTuning(instrumentId: String, tuningId: String) {
        let current = container.instruments.state
        if current.instrument.id == instrumentId && current.tuning.id == tuningId { return }
        resetHold()
        container.instruments.select(instrumentId: instrumentId, tuningId: tuningId)
        syncLowestFrequency()
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    public func saveCustomTuning(editingId: String?, name: String, notes: [Int]) -> String? {
        let instrument = container.instruments.state.instrument
        do {
            let validName = try validateTuningName(name)
            let validNotes = try validateNotes(notes, expectedCount: instrument.stringCount)
            container.instruments.saveTuning(editingId: editingId, instrumentId: instrument.id, name: validName, notes: validNotes)
            resetHold()
            syncLowestFrequency()
            rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    public func deleteCustomTuning(_ tuningId: String) {
        resetHold()
        container.instruments.deleteTuning(tuningId)
        syncLowestFrequency()
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    public func saveCustomInstrument(editingId: String?, name: String, stringCount: Int, notes: [Int]) -> String? {
        do {
            let validName = try validateInstrumentName(name)
            let validCount = try validateStringCount(stringCount)
            let validNotes = try validateNotes(notes, expectedCount: validCount)
            container.instruments.saveInstrument(editingId: editingId, name: validName, stringCount: validCount, notes: validNotes)
            resetHold()
            syncLowestFrequency()
            rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    public func deleteCustomInstrument(_ instrumentId: String) {
        resetHold()
        container.instruments.deleteInstrument(instrumentId)
        syncLowestFrequency()
        rebuildLiveStateFromLatest(now: currentUptimeMs(), allowNumericUpdate: true)
    }

    private func handleCapturedFrame(_ frame: PitchFrame) {
        latestFrame = frame
        let nextLevel = smoothedLevel(previous: inputLevel, rms: frame.inputLevel)
        if abs(nextLevel - inputLevel) > 0.0005 || (nextLevel == 0 && inputLevel != 0) {
            inputLevel = nextLevel
        }

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
            onConfirmed(reading: reading, settings: settings, currentMode: mode)
        }
        lockConfirmed = lock.confirmed

        applyLiveState(
            frame: frame,
            reading: reading,
            strings: strings,
            settings: settings,
            now: now,
            allowNumericUpdate: true
        )
    }

    private func rebuildLiveStateFromLatest(now: Int64, allowNumericUpdate: Bool) {
        let settings = container.tuner.value
        let strings = container.instruments.state.tuning.strings
        let reading = readFrame(
            frame: latestFrame,
            mode: mode,
            manualIndex: manualIndex,
            strings: strings,
            referencePitch: settings.referencePitch,
            previousTarget: previousTarget,
            capturing: capturing
        )
        applyLiveState(
            frame: latestFrame,
            reading: reading,
            strings: strings,
            settings: settings,
            now: now,
            allowNumericUpdate: allowNumericUpdate
        )
    }

    private func applyLiveState(
        frame: PitchFrame,
        reading: Reading,
        strings: [NamedFrequency],
        settings: TunerSettings,
        now: Int64,
        allowNumericUpdate: Bool
    ) {
        let tracking = frame.tracking
        let cents = reading.cents
        let withinTolerance = cents != nil && abs(cents!) <= Double(inTuneToleranceCents)
        let showTuned = settings.inTuneFeedback && lockConfirmed
        let tunedNow = (settings.inTuneFeedback
            ? showTuned
            : (tracking == .locked && withinTolerance)) && reading.activeIndex != nil

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

        if allowNumericUpdate {
            updateDisplayCents(
                cents: cents,
                noteMidi: reading.noteMidi,
                targetName: reading.targetName,
                tunedNow: tunedNow,
                now: now
            )
        }

        if activeIndex != reading.activeIndex {
            activeIndex = reading.activeIndex
        }

        let newLive = TunerLiveState(
            capturing: capturing,
            cents: cents,
            displayCents: throttledDisplayCents,
            needlePercent: needlePercentFromCents(cents),
            noteMidi: reading.noteMidi,
            targetMidi: targetMidi,
            frequency: frame.frequency,
            tuned: tunedNow,
            status: status,
            holding: tracking == .holding,
            pulse: pulseActive,
            tolerance: inTuneToleranceCents
        )
        if live != newLive {
            live = newLive
        }
    }

    private func updateDisplayCents(
        cents: Double?,
        noteMidi: Int?,
        targetName: String?,
        tunedNow: Bool,
        now: Int64
    ) {
        guard capturing, let cents else {
            resetDisplaySmoothing()
            return
        }

        let hadNoDisplay = throttledDisplayCents == nil
        let noteChanged = noteMidi != lastDisplayNoteMidi
        let targetChanged = targetName != lastDisplayTargetName
        let justTuned = tunedNow && !wasTunedForDisplay

        if hadNoDisplay || noteChanged || targetChanged || justTuned
            || (now - lastNumericUpdateMs >= Self.numericUpdateIntervalMs) {
            throttledDisplayCents = cents
            lastNumericUpdateMs = now
        }

        lastDisplayNoteMidi = noteMidi
        lastDisplayTargetName = targetName
        wasTunedForDisplay = tunedNow
    }

    private func resetDisplaySmoothing() {
        throttledDisplayCents = nil
        lastNumericUpdateMs = 0
        lastDisplayNoteMidi = nil
        lastDisplayTargetName = nil
        wasTunedForDisplay = false
    }

    private func onConfirmed(reading: Reading, settings: TunerSettings, currentMode: TunerMode) {
        lockPulseCount += 1
        if currentMode == .auto, let name = reading.targetName {
            tunedStrings.insert(name)
        }
        if settings.inTuneFeedback {
            pulseActive = true
            pulseTask?.cancel()
            pulseTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: .milliseconds(Self.lockPulseMs))
                guard !Task.isCancelled, let self else { return }
                self.pulseActive = false
                self.rebuildLiveStateFromLatest(now: self.currentUptimeMs(), allowNumericUpdate: false)
            }
            if settings.chime {
                container.noteAudio.playChime()
            }
        }
    }

    private func resetHold() {
        lock.reset()
        previousTarget = nil
        lockConfirmed = false
        tunedStrings = []
        pulseTask?.cancel()
        pulseTask = nil
        pulseActive = false
        resetDisplaySmoothing()
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
