import Foundation
import OmniTunerCore

public struct AppearanceSettings: Codable, Equatable, Sendable {
    public var themeMode: ThemeMode = .system
    public var accent: AccentColorPreset = .pink
}

public enum TunerMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case auto, manual
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .auto: "Auto"
        case .manual: "Manual"
        }
    }
}

public enum StartupMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case remember, auto, manual
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .remember: "Remember"
        case .auto: "Auto"
        case .manual: "Manual"
        }
    }
}

public let referencePitchRange = 415...466
public let defaultReferencePitch = 440
public let inTuneToleranceCents = 5
public let inTuneHoldMs: Int64 = 500

public struct TunerSettings: Codable, Equatable, Sendable {
    public var mode: TunerMode = .auto
    public var startupMode: StartupMode = .remember
    public var referencePitch: Int = defaultReferencePitch
    public var inTuneFeedback: Bool = true
    public var chime: Bool = true
    public var haptics: Bool = true
    public var proAccuracy: Bool = false

    public func initialMode() -> TunerMode {
        switch startupMode {
        case .remember: mode
        case .auto: .auto
        case .manual: .manual
        }
    }
}

public enum Accidental: String, CaseIterable, Codable, Sendable, Identifiable {
    case sharp, flat
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .sharp: "Sharps (♯)"
        case .flat: "Flats (♭)"
        }
    }
}

public enum LabelMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case noteNames, scaleDegrees
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .noteNames: "Notes"
        case .scaleDegrees: "Degrees"
        }
    }
}

public let fretCountOptions: [Int] = [12, 15, 21]

public struct ScaleSettings: Codable, Equatable, Sendable {
    public var rootPitchClass: Int = 4
    public var scaleId: String = "major"
    public var accidental: Accidental = .sharp
    public var fretCount: Int = 12
    public var labelMode: LabelMode = .noteNames
    public var showOutsideScale: Bool = false
}

public enum VoicingViewMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case fretboard, tab
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .fretboard: "Fretboard"
        case .tab: "Tab"
        }
    }
}

public enum DiagramLabels: String, CaseIterable, Codable, Sendable, Identifiable {
    case notes, degrees
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .notes: "Notes"
        case .degrees: "Degrees"
        }
    }
}

public struct ChordSettings: Codable, Equatable, Sendable {
    public var voicingCount: Int = defaultVoicingCount
    public var view: VoicingViewMode = .fretboard
    public var labels: DiagramLabels = .notes
}

@MainActor
@Observable
public final class PersistedStore<Value: Codable> {
    private let key: String
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public var value: Value {
        didSet { save() }
    }

    public init(key: String, defaultValue: Value, defaults: UserDefaults = .standard) {
        self.key = key
        self.defaults = defaults
        if let data = defaults.data(forKey: key),
           let decoded = try? decoder.decode(Value.self, from: data) {
            self.value = decoded
        } else {
            self.value = defaultValue
        }
    }

    public func update(_ transform: (inout Value) -> Void) {
        var next = value
        transform(&next)
        value = next
    }

    private func save() {
        if let data = try? encoder.encode(value) {
            defaults.set(data, forKey: key)
        }
    }
}
