public let bpmMin = 1
public let bpmMax = 800
public let bpmDefault = 100

public let denominators: [Int] = [2, 4, 8, 16]

public func isDenominator(_ value: Int) -> Bool {
    denominators.contains(value)
}

public let divisionsMin = 1
public let divisionsMax = 12

public struct SubdivisionOption: Sendable, Hashable, Identifiable {
    public var id: Int { n }
    public let n: Int
    public let label: String
    public let shortLabel: String

    public init(n: Int, label: String, shortLabel: String) {
        self.n = n
        self.label = label
        self.shortLabel = shortLabel
    }
}

public let subdivisions: [SubdivisionOption] = [
    SubdivisionOption(n: 1, label: "Beat only", shortLabel: "beat"),
    SubdivisionOption(n: 2, label: "Eighths", shortLabel: "8ths"),
    SubdivisionOption(n: 3, label: "Triplets", shortLabel: "trips"),
    SubdivisionOption(n: 4, label: "Sixteenths", shortLabel: "16ths"),
    SubdivisionOption(n: 5, label: "Quintuplets", shortLabel: "quint"),
    SubdivisionOption(n: 6, label: "Sextuplets", shortLabel: "sext"),
    SubdivisionOption(n: 7, label: "Septuplets", shortLabel: "sept"),
]

public let patternMinBars = 1
public let patternMaxBars = 16

public let polyMin = 1
public let polyMax = 32

public struct TimeSignature: Sendable, Hashable, Codable {
    public var numerator: Int
    public var denominator: Int

    public init(numerator: Int, denominator: Int) {
        self.numerator = numerator
        self.denominator = denominator
    }
}

public struct PolyState: Sendable, Hashable, Codable {
    public var enabled: Bool
    public var events: Int
    public var accentFirst: Bool

    public init(enabled: Bool, events: Int, accentFirst: Bool) {
        self.enabled = enabled
        self.events = events
        self.accentFirst = accentFirst
    }
}

public struct SoundRole: Sendable, Hashable, Codable {
    public var id: String
    public var vol: Double
    public var accentVol: Double?

    public init(id: String, vol: Double, accentVol: Double? = nil) {
        self.id = id
        self.vol = vol
        self.accentVol = accentVol
    }
}

public struct MetronomeSoundRoles: Sendable, Hashable, Codable {
    public var downbeat: SoundRole
    public var beat: SoundRole
    public var subdivision: SoundRole
    public var poly: SoundRole

    public init(downbeat: SoundRole, beat: SoundRole, subdivision: SoundRole, poly: SoundRole) {
        self.downbeat = downbeat
        self.beat = beat
        self.subdivision = subdivision
        self.poly = poly
    }
}

public struct TempoRamp: Sendable, Hashable, Codable {
    public var enabled: Bool
    public var targetBpm: Double
    public var bars: Int

    public init(enabled: Bool, targetBpm: Double, bars: Int) {
        self.enabled = enabled
        self.targetBpm = targetBpm
        self.bars = bars
    }
}

public let rampBarsMin = 1
public let rampBarsMax = 32

public let defaultMetronomeRamp = TempoRamp(enabled: false, targetBpm: 120.0, bars: 8)

public struct MetronomeState: Sendable, Hashable, Codable {
    public var bpm: Double
    public var timeSignature: TimeSignature
    public var divisionsPerBeat: Int
    public var barPattern: [Int]
    public var poly: PolyState
    public var sounds: MetronomeSoundRoles
    public var masterVol: Double
    public var countIn: Bool
    public var ramp: TempoRamp

    public init(
        bpm: Double,
        timeSignature: TimeSignature,
        divisionsPerBeat: Int,
        barPattern: [Int],
        poly: PolyState,
        sounds: MetronomeSoundRoles,
        masterVol: Double,
        countIn: Bool,
        ramp: TempoRamp
    ) {
        self.bpm = bpm
        self.timeSignature = timeSignature
        self.divisionsPerBeat = divisionsPerBeat
        self.barPattern = barPattern
        self.poly = poly
        self.sounds = sounds
        self.masterVol = masterVol
        self.countIn = countIn
        self.ramp = ramp
    }
}

public struct MetronomePreset: Sendable, Hashable, Codable, Identifiable {
    public let id: String
    public let name: String
    public let state: MetronomeState

    public init(id: String, name: String, state: MetronomeState) {
        self.id = id
        self.name = name
        self.state = state
    }
}

public let presetsMax = 50

public let defaultMetronomeSounds = MetronomeSoundRoles(
    downbeat: SoundRole(id: "beep-hi", vol: 1.0),
    beat: SoundRole(id: "beep-mid", vol: 0.8),
    subdivision: SoundRole(id: "beep-lo", vol: 0.5),
    poly: SoundRole(id: "shaker", vol: 0.55, accentVol: 1.0)
)

public let defaultMetronomeState = MetronomeState(
    bpm: Double(bpmDefault),
    timeSignature: TimeSignature(numerator: 4, denominator: 4),
    divisionsPerBeat: 1,
    barPattern: [1],
    poly: PolyState(enabled: false, events: 3, accentFirst: true),
    sounds: defaultMetronomeSounds,
    masterVol: 0.9,
    countIn: false,
    ramp: defaultMetronomeRamp
)

public let meterPresets: [TimeSignature] = [
    TimeSignature(numerator: 2, denominator: 4),
    TimeSignature(numerator: 3, denominator: 4),
    TimeSignature(numerator: 4, denominator: 4),
    TimeSignature(numerator: 5, denominator: 4),
    TimeSignature(numerator: 6, denominator: 8),
    TimeSignature(numerator: 7, denominator: 8),
    TimeSignature(numerator: 9, denominator: 8),
    TimeSignature(numerator: 10, denominator: 16),
    TimeSignature(numerator: 11, denominator: 8),
    TimeSignature(numerator: 12, denominator: 8),
]

public struct PatternPreset: Sendable, Hashable {
    public let label: String
    public let bars: [Int]

    public init(label: String, bars: [Int]) {
        self.label = label
        self.bars = bars
    }
}

public let patternPresets: [PatternPreset] = [
    PatternPreset(label: "All bars", bars: [1]),
    PatternPreset(label: "Alternate", bars: [1, 0]),
    PatternPreset(label: "Play 2, mute 1", bars: [1, 1, 0]),
    PatternPreset(label: "Play 2, mute 2", bars: [1, 1, 0, 0]),
    PatternPreset(label: "Play 3, mute 1", bars: [1, 1, 1, 0]),
    PatternPreset(label: "Play 4, mute 4", bars: [1, 1, 1, 1, 0, 0, 0, 0]),
]
