import Foundation

public struct MeterModel: Sendable, Hashable {
    public let numerator: Int
    public let denominator: Int
    public let compound: Bool
    public let divisionsPerBeat: Int
    public let beatsPerBar: Int
    public let beatQuarters: Double
    public let barQuarters: Double
}

private let noteNameByDenominator: [Int: String] = [
    2: "half",
    4: "quarter",
    8: "eighth",
    16: "sixteenth",
]

private func denominatorNoteName(_ denominator: Int) -> String {
    noteNameByDenominator[denominator] ?? "1/\(denominator)"
}

public func meterModel(numerator: Int, denominator: Int) -> MeterModel {
    let unitQuarters = 4.0 / Double(denominator)
    let compound = numerator % 3 == 0 && numerator >= 6 && denominator >= 8
    let divisionsPerBeat = compound ? 3 : 1
    let beatsPerBar = compound ? numerator / 3 : numerator
    return MeterModel(
        numerator: numerator,
        denominator: denominator,
        compound: compound,
        divisionsPerBeat: divisionsPerBeat,
        beatsPerBar: beatsPerBar,
        beatQuarters: unitQuarters * Double(divisionsPerBeat),
        barQuarters: unitQuarters * Double(numerator)
    )
}

public func describeMeter(_ model: MeterModel) -> String {
    if model.compound {
        return "Compound \(model.numerator)/\(model.denominator) — \(model.beatsPerBar) dotted-\(denominatorNoteName(model.denominator)) beats per bar"
    }
    let plural = model.beatsPerBar > 1 ? "s" : ""
    return "\(model.numerator)/\(model.denominator) — \(model.beatsPerBar) \(denominatorNoteName(model.denominator))-note beat\(plural) per bar"
}

public struct BarEvent: Sendable, Hashable {
    public let beats: Double
    public let layer: String
    public let role: String

    public init(beats: Double, layer: String, role: String) {
        self.beats = beats
        self.layer = layer
        self.role = role
    }
}

public struct PolyEvents: Sendable, Hashable {
    public let enabled: Bool
    public let events: Int
    public let accentFirst: Bool

    public init(enabled: Bool, events: Int, accentFirst: Bool = true) {
        self.enabled = enabled
        self.events = events
        self.accentFirst = accentFirst
    }
}

public func buildBarEvents(
    model: MeterModel,
    subdivision: Int = 1,
    poly: PolyEvents? = nil
) -> [BarEvent] {
    var events: [BarEvent] = []
    for b in 0..<model.beatsPerBar {
        for s in 0..<subdivision {
            let role: String
            if b == 0 && s == 0 {
                role = "downbeat"
            } else if s == 0 {
                role = "beat"
            } else {
                role = "subdivision"
            }
            events.append(BarEvent(beats: Double(b) + Double(s) / Double(subdivision), layer: "meter", role: role))
        }
    }
    if let poly, poly.enabled, poly.events > 0 {
        let span = Double(model.beatsPerBar)
        for i in 0..<poly.events {
            events.append(
                BarEvent(
                    beats: (Double(i) * span) / Double(poly.events),
                    layer: "poly",
                    role: (i == 0 && poly.accentFirst) ? "polyAccent" : "poly"
                )
            )
        }
    }
    events.sort { a, b in
        if a.beats != b.beats { return a.beats < b.beats }
        return a.layer == "meter" && b.layer != "meter"
    }
    return events
}

public func quarterDuration(bpm: Double) -> Double { 60.0 / bpm }

public func beatDuration(bpm: Double, model: MeterModel) -> Double { (60.0 / bpm) * model.beatQuarters }

public func barDuration(bpm: Double, model: MeterModel) -> Double { (60.0 / bpm) * model.barQuarters }

public func subdivisionInterval(bpm: Double, model: MeterModel, divisionsPerBeat: Int) -> Double {
    beatDuration(bpm: bpm, model: model) / Double(divisionsPerBeat)
}

public func ticksPerBar(model: MeterModel, divisionsPerBeat: Int) -> Int {
    model.beatsPerBar * divisionsPerBeat
}

public func tickKind(tickIndexInBar: Int, divisionsPerBeat: Int) -> String {
    if tickIndexInBar == 0 { return "downbeat" }
    if tickIndexInBar % divisionsPerBeat == 0 { return "beat" }
    return "subdivision"
}

public func polyTimes(barStart: Double, barDur: Double, count: Int) -> [Double] {
    guard count > 0 else { return [] }
    let step = barDur / Double(count)
    return (0..<count).map { barStart + Double($0) * step }
}

public func isBarAudible(barIndex: Int, pattern: [Int]) -> Bool {
    guard !pattern.isEmpty else { return true }
    return pattern[barIndex % pattern.count] == 1
}

public func tapBpm(intervalsMs: [Double]) -> Double? {
    let valid = intervalsMs.filter { $0 > 120 && $0 < 2500 }
    guard !valid.isEmpty else { return nil }
    let avg = valid.reduce(0.0, +) / Double(valid.count)
    return Double(Int(floor(60000.0 / avg + 0.5)))
}

public func formatBarDuration(ms: Double) -> String {
    if ms < 1000 { return "\(Int(floor(ms + 0.5))) ms" }
    let seconds = ms / 1000.0
    let whole = Int(seconds)
    let frac = Int(floor((seconds - Double(whole)) * 100.0 + 0.5))
    return String(format: "%d.%02d s", whole, frac)
}

public func getTempoMarking(bpm: Double) -> String {
    switch bpm {
    case ..<40: return "Grave"
    case ..<60: return "Lento"
    case ..<76: return "Adagio"
    case ..<108: return "Andante"
    case ..<120: return "Moderato"
    case ..<156: return "Allegro"
    case ..<200: return "Presto"
    default: return "Prestissimo"
    }
}
