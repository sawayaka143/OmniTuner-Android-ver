import Foundation
import OmniTunerCore
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public enum SoundSlot: String, CaseIterable, Identifiable, Sendable {
    case downbeat = "Downbeat"
    case beat = "Beat"
    case subdivision = "Subdivision"
    case poly = "Poly"

    public var id: String { rawValue }
}

public extension MetronomeSoundRoles {
    func role(for slot: SoundSlot) -> SoundRole {
        switch slot {
        case .downbeat: downbeat
        case .beat: beat
        case .subdivision: subdivision
        case .poly: poly
        }
    }

    func replacing(_ slot: SoundSlot, with role: SoundRole) -> MetronomeSoundRoles {
        var copy = self
        switch slot {
        case .downbeat: copy.downbeat = role
        case .beat: copy.beat = role
        case .subdivision: copy.subdivision = role
        case .poly: copy.poly = role
        }
        return copy
    }
}

public struct MetronomeUiState: Sendable {
    public var state: MetronomeState = defaultMetronomeState
    public var presets: [MetronomePreset] = []
    public var isPlaying: Bool = false
    public var barIndex: Int = 0
    public var patternPos: Int = 0
    public var progress: Double = 0.0
    public var inCountIn: Bool = false
    public var barActive: Bool = true
    public var pulsesPerBar: Int = 0
    public var pulsesPerBeat: Int = 1
}

@MainActor
@Observable
public final class MetronomeModelView {
    private let container: AppContainer
    private let engine = MetronomeAudioEngine()

    public private(set) var isPlaying = false
    private var barIndex = 0
    private var patternPos = 0
    private var progress = 0.0
    private var inCountIn = false
    private var barActive = true
    private var pulsesPerBar = 0
    private var pulsesPerBeat = 1

    private var tapTimestampsMs: [Double] = []

    public init(container: AppContainer) {
        self.container = container
        engine.configure(container.metronome.value)
        engine.setTransportHandler { [weak self] update in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.barIndex = update.barIndex
                self.patternPos = update.patternPos
                self.progress = update.progress
                self.inCountIn = update.countIn
                self.barActive = update.barActive
                self.pulsesPerBar = update.pulsesPerBar
                self.pulsesPerBeat = update.pulsesPerBeat
            }
        }
    }

    public var ui: MetronomeUiState {
        MetronomeUiState(
            state: container.metronome.value,
            presets: container.metronomePresets.value,
            isPlaying: isPlaying,
            barIndex: barIndex,
            patternPos: patternPos,
            progress: progress,
            inCountIn: inCountIn,
            barActive: barActive,
            pulsesPerBar: pulsesPerBar,
            pulsesPerBeat: pulsesPerBeat
        )
    }

    public func toggle() {
        if isPlaying {
            stop()
        } else {
            let current = container.metronome.value
            engine.configure(current)
            engine.start(with: current)
            isPlaying = true
            #if canImport(UIKit)
            UIApplication.shared.isIdleTimerDisabled = true
            #endif
        }
    }

    public func stop() {
        guard isPlaying else { return }
        engine.stop()
        isPlaying = false
        barIndex = 0
        patternPos = 0
        progress = 0
        inCountIn = false
        barActive = true
        pulsesPerBar = 0
        pulsesPerBeat = 1
        #if canImport(UIKit)
        UIApplication.shared.isIdleTimerDisabled = false
        #endif
    }

    public func setBackground(_ value: Bool) {
        engine.setBackground(value)
    }

    public func previewVoice(id: String, vol: Double) {
        engine.previewVoice(id: id, vol: vol, noteAudio: container.noteAudio)
    }

    public func registerTap() {
        let nowMs = ProcessInfo.processInfo.systemUptime * 1000.0
        if let last = tapTimestampsMs.last, nowMs - last > 2500 {
            tapTimestampsMs.removeAll()
        }
        tapTimestampsMs.append(nowMs)
        if tapTimestampsMs.count > 8 {
            tapTimestampsMs.removeFirst()
        }
        guard tapTimestampsMs.count >= 2 else { return }
        var intervals: [Double] = []
        for i in 1..<tapTimestampsMs.count {
            intervals.append(tapTimestampsMs[i] - tapTimestampsMs[i - 1])
        }
        if let bpm = tapBpm(intervalsMs: intervals) {
            setBpm(bpm)
        }
    }

    public func setBpm(_ bpm: Double) {
        edit { $0.bpm = min(Double(bpmMax), max(Double(bpmMin), bpm)) }
    }

    public func changeBpm(_ delta: Int) {
        edit { $0.bpm = min(Double(bpmMax), max(Double(bpmMin), $0.bpm + Double(delta))) }
    }

    public func setTimeSignature(numerator: Int, denominator: Int) {
        edit { $0.timeSignature = TimeSignature(numerator: numerator, denominator: denominator) }
    }

    public func changeNumerator(_ delta: Int) {
        edit {
            $0.timeSignature.numerator = min(32, max(1, $0.timeSignature.numerator + delta))
        }
    }

    public func setDivisionsPerBeat(_ value: Int) {
        edit { $0.divisionsPerBeat = min(divisionsMax, max(divisionsMin, value)) }
    }

    public func setBarPattern(_ pattern: [Int]) {
        edit { $0.barPattern = pattern.isEmpty ? [1] : pattern }
    }

    public func setPolyEnabled(_ enabled: Bool) {
        edit { $0.poly.enabled = enabled }
    }

    public func setPolyAccentFirst(_ accentFirst: Bool) {
        edit { $0.poly.accentFirst = accentFirst }
    }

    public func changePolyEvents(_ delta: Int) {
        edit { $0.poly.events = min(polyMax, max(polyMin, $0.poly.events + delta)) }
    }

    public func setVoice(slot: SoundSlot, voiceId: String) {
        edit {
            var role = $0.sounds.role(for: slot)
            role.id = voiceId
            $0.sounds = $0.sounds.replacing(slot, with: role)
        }
    }

    public func setSlotVolume(slot: SoundSlot, volume: Double) {
        edit {
            var role = $0.sounds.role(for: slot)
            role.vol = min(1.0, max(0.0, volume))
            $0.sounds = $0.sounds.replacing(slot, with: role)
        }
    }

    public func setMasterVolume(_ volume: Double) {
        edit { $0.masterVol = min(1.0, max(0.0, volume)) }
    }

    public func setCountIn(_ enabled: Bool) {
        edit { $0.countIn = enabled }
    }

    public func setRampEnabled(_ enabled: Bool) {
        edit { $0.ramp.enabled = enabled }
    }

    public func setRampTargetBpm(_ bpm: Int) {
        edit { $0.ramp.targetBpm = Double(min(bpmMax, max(bpmMin, bpm))) }
    }

    public func setRampBars(_ bars: Int) {
        edit { $0.ramp.bars = min(rampBarsMax, max(rampBarsMin, bars)) }
    }

    public func savePreset(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = trimmed.isEmpty ? "Preset \(container.metronomePresets.value.count + 1)" : trimmed
        let preset = MetronomePreset(
            id: "mp-\(UUID().uuidString)",
            name: title,
            state: container.metronome.value
        )
        container.metronomePresets.update { list in
            list = Array(([preset] + list).prefix(presetsMax))
        }
    }

    public func applyPreset(id: String) {
        guard let preset = container.metronomePresets.value.first(where: { $0.id == id }) else { return }
        edit { $0 = preset.state }
    }

    public func deletePreset(id: String) {
        container.metronomePresets.update { $0.removeAll { $0.id == id } }
    }

    private func edit(_ transform: (inout MetronomeState) -> Void) {
        container.metronome.update(transform)
        engine.configure(container.metronome.value)
    }
}
