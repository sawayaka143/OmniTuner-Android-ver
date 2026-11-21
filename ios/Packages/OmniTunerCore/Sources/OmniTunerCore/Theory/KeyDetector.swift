public struct DetectedKey: Sendable, Hashable {
    public let tonicPc: Int
    public let tonicName: String
    public let mode: ModeName
    public let score: Int
    public let coverage: Double
    public let confidence: String
    public var alternatives: [DetectedKey]

    public init(
        tonicPc: Int,
        tonicName: String,
        mode: ModeName,
        score: Int,
        coverage: Double,
        confidence: String,
        alternatives: [DetectedKey]
    ) {
        self.tonicPc = tonicPc
        self.tonicName = tonicName
        self.mode = mode
        self.score = score
        self.coverage = coverage
        self.confidence = confidence
        self.alternatives = alternatives
    }
}

public let modePriority: [ModeName: Int] = [
    .ionian: 0,
    .aeolian: 1,
    .dorian: 2,
    .mixolydian: 3,
    .lydian: 4,
    .phrygian: 5,
    .locrian: 6,
]

private func confidenceFor(_ coverage: Double) -> String {
    if coverage >= 0.85 { return "strong" }
    if coverage >= 0.6 { return "moderate" }
    return "weak"
}

private let flatMajorPcs: Set<Int> = [5, 10, 3, 8, 1, 6]

private func modeUsesFlats(tonicPc: Int, mode: ModeName) -> Bool {
    flatMajorPcs.contains(mod12(tonicPc - ModeName.ionian.steps[mode.ordinal]))
}

public func rankKeys(_ chords: [ParsedChord], tuningFlats: Bool? = nil) -> [DetectedKey] {
    guard !chords.isEmpty else { return [] }
    let hasFlatChord = chords.contains(where: \.flats)
    let tonicPcs = Set(chords.map(\.rootPc))
    var candidates: [DetectedKey] = []

    for tonicPc in 0..<12 {
        let tonicPresent = tonicPcs.contains(tonicPc)
        for mode in modeNames {
            let useFlats = hasFlatChord || (tuningFlats ?? modeUsesFlats(tonicPc: tonicPc, mode: mode))
            let tonicName = pcName(tonicPc, flats: useFlats || tuningFlats == true)
            var score = 0
            var good = 0
            for chord in chords {
                let badge = computeBadgeForPc(
                    chord: chord,
                    tonicPc: tonicPc,
                    modeName: mode,
                    tuningFlats: tuningFlats == true,
                    tonicFlats: useFlats
                )
                switch badge.kind {
                case .good where badge.harmonicMinor && !tonicPresent:
                    score += 1
                case .good:
                    score += 2
                    good += 1
                case .warn where chord.rootPc == tonicPc:
                    break
                case .warn:
                    score += 1
                case .borrowed:
                    if tonicPresent { score += 1 }
                case .bad:
                    break
                }
            }
            let coverage = Double(good) / Double(chords.count)
            candidates.append(
                DetectedKey(
                    tonicPc: tonicPc,
                    tonicName: tonicName,
                    mode: mode,
                    score: score,
                    coverage: coverage,
                    confidence: confidenceFor(coverage),
                    alternatives: []
                )
            )
        }
    }

    candidates.sort { a, b in
        if a.score != b.score { return a.score > b.score }
        if a.coverage != b.coverage { return a.coverage > b.coverage }
        let pa = modePriority[a.mode] ?? 99
        let pb = modePriority[b.mode] ?? 99
        if pa != pb { return pa < pb }
        return a.tonicPc < b.tonicPc
    }

    return candidates
}

public func detectKey(_ chords: [ParsedChord], tuningFlats: Bool? = nil) -> DetectedKey? {
    let ranked = rankKeys(chords, tuningFlats: tuningFlats)
    guard !ranked.isEmpty else { return nil }
    var top = ranked[0]
    let alts = Array(ranked.dropFirst().prefix(2))
    if top.score == 0 {
        return DetectedKey(
            tonicPc: top.tonicPc,
            tonicName: top.tonicName,
            mode: top.mode,
            score: top.score,
            coverage: top.coverage,
            confidence: "weak",
            alternatives: alts
        )
    } else {
        top.alternatives = alts
        return top
    }
}
