import Foundation

public struct StringTarget: Sendable, Hashable {
    public let name: String
    public let midi: Int
    public let cents: Double

    public init(name: String, midi: Int, cents: Double) {
        self.name = name
        self.midi = midi
        self.cents = cents
    }
}

public let hysteresisCents = 10.0

public func midiNoteToFrequency(_ midiNote: Int, ref: Double = 440.0) -> Double {
    ref * pow(2.0, Double(midiNote - 69) / 12.0)
}

public func frequencyToMidiNote(_ frequency: Double, ref: Double = 440.0) -> Int? {
    guard frequency.isFinite, frequency > 0.0 else { return nil }
    return Int((69.0 + 12.0 * log2(frequency / ref)).rounded())
}

public func frequencyToMidiFloat(_ frequency: Double, ref: Double = 440.0) -> Double? {
    guard frequency.isFinite, frequency > 0.0 else { return nil }
    return 69.0 + 12.0 * log2(frequency / ref)
}

public func centsFromMidiFloat(_ playedMidiFloat: Double?, targetMidi: Int) -> Double? {
    guard let playedMidiFloat else { return nil }
    return (playedMidiFloat - Double(targetMidi)) * 100.0
}

public func nearestStringTarget(
    playedMidiFloat: Double?,
    strings: [NamedFrequency],
    previousName: String? = nil
) -> StringTarget? {
    guard let playedMidiFloat, playedMidiFloat.isFinite, !strings.isEmpty else {
        return nil
    }

    var best: StringTarget?
    var bestDistance = Double.infinity
    var previousMidi: Int?
    var previousDistance = Double.infinity

    for string in strings {
        let midi = frequencyToMidiNote(string.freq) ?? 69
        let cents = centsFromMidiFloat(playedMidiFloat, targetMidi: midi) ?? 0.0
        let distance = abs(cents)
        if string.name == previousName {
            previousMidi = midi
            previousDistance = distance
        }
        if distance < bestDistance {
            bestDistance = distance
            best = StringTarget(name: string.name, midi: midi, cents: cents)
        }
    }

    guard let best else { return nil }
    if let previousMidi, let previousName, previousDistance <= bestDistance + hysteresisCents {
        let cents = centsFromMidiFloat(playedMidiFloat, targetMidi: previousMidi) ?? 0.0
        return StringTarget(name: previousName, midi: previousMidi, cents: cents)
    }
    return best
}

public func needlePercentFromCents(_ cents: Double?) -> Double {
    guard let cents, cents.isFinite else { return 50.0 }
    return 50.0 + min(50.0, max(-50.0, cents))
}

public func shouldConfirm(inRange: Bool, elapsedMs: Int64, holdMs: Int64) -> Bool {
    inRange && elapsedMs >= holdMs
}

public func midiNoteLabel(_ midiNote: Int) -> String {
    let semitoneFromA = midiNote - 69
    let noteIndex = ((semitoneFromA % 12) + 12) % 12
    let octave = Int(floor(Double(midiNote) / 12.0)) - 1
    return "\(noteNames[noteIndex])\(octave)"
}

private func formatTwoDecimals(_ value: Double) -> String {
    let scaled = Int((value * 100.0).rounded())
    let whole = scaled / 100
    let frac = abs(scaled) % 100
    return String(format: "%d.%02d", whole, frac)
}

public func hzDisplay(_ frequency: Double?) -> String {
    guard let frequency else { return "— Hz" }
    return "\(formatTwoDecimals(frequency)) Hz"
}

public func nearestSemitone(_ playedMidiFloat: Double?) -> Int? {
    guard let playedMidiFloat, playedMidiFloat.isFinite else { return nil }
    return Int(playedMidiFloat.rounded())
}

public func tuneDirectionText(_ cents: Double?, threshold: Double = 5.0) -> String {
    guard let cents, cents.isFinite else { return "—" }
    if abs(cents) <= threshold { return "IN TUNE" }
    return cents < 0 ? "TUNE UP" : "TUNE DOWN"
}

public func tuneCentsText(_ cents: Double?, threshold: Double = 5.0) -> String {
    guard let cents, cents.isFinite else { return "" }
    if abs(cents) <= threshold { return "" }
    return "\(abs(Int(cents.rounded())))¢"
}

public func tuneColorProgress(_ cents: Double?, threshold: Double = 5.0) -> Double {
    guard let cents, cents.isFinite else { return 0.0 }
    let magnitude = abs(cents)
    let endpoint = max(5.0, threshold)
    if endpoint >= 50.0 { return magnitude <= endpoint ? 1.0 : 0.0 }
    return min(1.0, max(0.0, (50.0 - magnitude) / (50.0 - endpoint)))
}
