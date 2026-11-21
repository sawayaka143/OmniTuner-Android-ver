public let maxFret = 12
public let defaultVoicingCount = 6
public let minVoicingCount = 1
public let maxVoicingCount = 12

private let maxSpan = 4
private let maxThumbReach = 4
private let maxFingers = 4
private let minNotes = 3
private let minDistinct = 3
private let openStringMaxFret = 3
private let firstPositionMaxFret = 4
private let comfortableSpan = 2
private let comfortableRange = 24
private let reverseStretchFrets = 3
private let maxInteriorMutes = 2
private let maxSoundingStrings = 8
private let rankedKeep = 400

private enum Weights {
    static let rootInBass = 100
    static let otherBass = -150
    static let perNote = 14
    static let perDistinctPc = 8
    static let perFinger = -8
    static let perBarre = -55
    static let perPositionFret = -6
    static let perInteriorMute = -30
    static let perOutOfPositionOpen = -30
    static let perSpanFretBeyondComfort = -15
    static let perSemitoneBeyondRange = -2
    static let perReverseStretchFret = -50
    static let extensionOnTop = 10
    static let standardGrip = 45
}

public let extensionIntervals: Set<Int> = [13, 14, 15, 17, 18, 20, 21]
private let alteredExtensionIntervals: Set<Int> = [13, 15, 18, 20]

public struct SoundingNote: Sendable, Hashable {
    public let stringIndex: Int
    public let fret: Int
    public let midi: Int

    public init(stringIndex: Int, fret: Int, midi: Int) {
        self.stringIndex = stringIndex
        self.fret = fret
        self.midi = midi
    }
}

public struct VoicingShape: Sendable, Hashable {
    public let frets: [Int?]
    public let sounding: [SoundingNote]
    public let span: Int
    public let bassMidi: Int
    public let bassIsRoot: Bool
    public let position: Int
    public let openCount: Int
    public let cost: Int

    public init(
        frets: [Int?],
        sounding: [SoundingNote],
        span: Int,
        bassMidi: Int,
        bassIsRoot: Bool,
        position: Int,
        openCount: Int,
        cost: Int
    ) {
        self.frets = frets
        self.sounding = sounding
        self.span = span
        self.bassMidi = bassMidi
        self.bassIsRoot = bassIsRoot
        self.position = position
        self.openCount = openCount
        self.cost = cost
    }
}

public struct Fingering: Sendable, Hashable {
    public let fingers: Int
    public let barres: Int
}

private struct ShapeFeatures {
    let frets: [Int?]
    let sounding: [SoundingNote]
    let pcs: Set<Int>
    let bassMidi: Int
    let topPc: Int
    let range: Int
    let position: Int
    let maxFret: Int
    let span: Int
    let openCount: Int
    let interiorMutes: Int
    let fingering: Fingering
}

private struct RankedShape {
    let features: ShapeFeatures
    let score: Int
}

private func fretsKey(_ frets: [Int?]) -> String {
    frets.map { $0.map(String.init) ?? "x" }.joined(separator: ",")
}

public func instantiateForm(_ form: ChordForm, tuning: ParsedTuning, rootPc: Int) -> [Int?]? {
    guard form.offsets.count == tuning.midi.count else { return nil }
    let anchorFret = mod12(rootPc - tuning.midi[form.anchor])
    var frets: [Int?] = []
    frets.reserveCapacity(form.offsets.count)
    for offset in form.offsets {
        guard let offset else {
            frets.append(nil)
            continue
        }
        let fret = anchorFret + offset
        if fret < 0 || fret > maxFret { return nil }
        frets.append(fret)
    }
    return frets
}

public func requiredPitchClasses(_ chord: ParsedChord) -> Set<Int> {
    let optional = Set(chord.optionalPcs)
    let hasThird = chord.intervals.contains(3) || chord.intervals.contains(4)
    let hasGuideTone = [9, 10, 11].contains { chord.intervals.contains($0) }
    let dropFifth = hasThird && hasGuideTone
    var result: Set<Int> = []
    for (index, pc) in chord.pcs.enumerated() {
        let interval = chord.intervals[index]
        if !optional.contains(pc) && !extensionIntervals.contains(interval) && !(dropFifth && interval == 7) {
            result.insert(pc)
        }
    }
    return result
}

public func requiredExtensions(_ chord: ParsedChord) -> [Int] {
    let extensions = chord.intervals.filter { extensionIntervals.contains($0) }
    guard let highest = extensions.max() else { return [] }
    var result: [Int] = []
    for (index, pc) in chord.pcs.enumerated() {
        let interval = chord.intervals[index]
        if interval == highest || alteredExtensionIntervals.contains(interval) {
            result.append(pc)
        }
    }
    return result
}

public func isNarrowingOf(_ narrow: [Int?], wide: [Int?]) -> Bool {
    narrow.indices.allSatisfy { i in
        narrow[i] == nil || (i < wide.count && narrow[i] == wide[i])
    }
}

public func analyseFingering(_ frets: [Int?]) -> Fingering? {
    let fretted = frets.indices.filter { (frets[$0] ?? 0) > 0 }
    if fretted.count <= maxFingers { return Fingering(fingers: fretted.count, barres: 0) }

    var seenFrets: [Int] = []
    for s in fretted {
        if let f = frets[s], !seenFrets.contains(f) { seenFrets.append(f) }
    }

    var groupSizes: [Int] = []
    for fret in seenFrets {
        let strings = fretted.filter { frets[$0] == fret }
        var size = 1
        for k in 1..<strings.count {
            var blocked = false
            for s in (strings[k - 1] + 1)..<strings[k] {
                let between = frets[s]
                if between == 0 || (between != nil && between! < fret) {
                    blocked = true
                }
            }
            if blocked {
                groupSizes.append(size)
                size = 1
            } else {
                size += 1
            }
        }
        groupSizes.append(size)
    }

    var fingers = fretted.count
    var barres = 0
    for size in groupSizes.filter({ $0 > 1 }).sorted(by: >) {
        if fingers <= maxFingers { break }
        fingers -= (size - 1)
        barres += 1
    }
    return fingers <= maxFingers ? Fingering(fingers: fingers, barres: barres) : nil
}

public func handFits(_ prefix: [Int?], stringCount: Int) -> Bool {
    fingerGroups(prefix) <= maxFingers && spanFits(prefix, stringCount: stringCount)
}

private func fingerGroups(_ frets: [Int?]) -> Int {
    var groups = 0
    for s in frets.indices {
        guard let fret = frets[s], fret > 0 else { continue }
        var joins = false
        for p in stride(from: s - 1, through: 0, by: -1) {
            guard let other = frets[p] else { continue }
            if other == 0 || other < fret { break }
            if other == fret {
                joins = true
                break
            }
        }
        if !joins { groups += 1 }
    }
    return groups
}

private func spanFits(_ frets: [Int?], stringCount: Int) -> Bool {
    var low = Int.max
    var high = Int.min
    for s in 1..<frets.count {
        guard let fret = frets[s], fret > 0 else { continue }
        low = min(low, fret)
        high = max(high, fret)
    }
    let others = low <= high
    if others && high - low > maxSpan { return false }
    guard let thumb = frets.first ?? nil, thumb > 0 else { return true }
    if !others || max(high, thumb) - min(low, thumb) <= maxSpan { return true }
    if stringCount < 4 || thumb > low { return false }
    return high - thumb <= maxSpan + maxThumbReach
}

private func describeShape(_ frets: [Int?], tuning: ParsedTuning) -> ShapeFeatures? {
    guard let fingering = analyseFingering(frets) else { return nil }
    var sounding: [SoundingNote] = []
    for s in frets.indices {
        if let f = frets[s] {
            sounding.append(SoundingNote(stringIndex: s, fret: f, midi: tuning.midi[s] + f))
        }
    }
    guard let firstSounding = sounding.first, let lastSounding = sounding.last else { return nil }
    let midis = sounding.map(\.midi)
    let pressed = sounding.map(\.fret).filter { $0 > 0 }
    let position = pressed.min() ?? 0
    let highestFret = pressed.max() ?? 0
    let minMidi = midis.min()!
    let maxMidi = midis.max()!
    return ShapeFeatures(
        frets: frets,
        sounding: sounding,
        pcs: Set(midis.map { mod12($0) }),
        bassMidi: minMidi,
        topPc: mod12(maxMidi),
        range: maxMidi - minMidi,
        position: position,
        maxFret: highestFret,
        span: highestFret - position,
        openCount: sounding.filter { $0.fret == 0 }.count,
        interiorMutes: lastSounding.stringIndex - firstSounding.stringIndex + 1 - sounding.count,
        fingering: fingering
    )
}

private func compareRanked(_ a: RankedShape, _ b: RankedShape) -> Bool {
    if a.score != b.score { return a.score > b.score }
    if a.features.sounding.count != b.features.sounding.count {
        return a.features.sounding.count > b.features.sounding.count
    }
    if a.features.span != b.features.span { return a.features.span < b.features.span }
    if a.features.maxFret != b.features.maxFret { return a.features.maxFret < b.features.maxFret }
    return fretsKey(a.features.frets) < fretsKey(b.features.frets)
}

private final class VoicingSearch {
    private let tuning: ParsedTuning
    private let chord: ParsedChord
    private let chordPcs: Set<Int>
    private let requiredPcs: Set<Int>
    private let extensionPcs: Set<Int>
    private let bassPc: Int?
    private let allowedPcs: Set<Int>
    private let hasBassString: Bool
    private let standardGrips: Set<String>

    init(tuning: ParsedTuning, chord: ParsedChord, enforceBass: Bool, enforceExtensions: Bool) {
        self.tuning = tuning
        self.chord = chord
        let cPcs = Set(chord.pcs)
        self.chordPcs = cPcs
        var req = requiredPitchClasses(chord)
        if enforceExtensions {
            for ext in requiredExtensions(chord) { req.insert(ext) }
        }
        self.requiredPcs = req
        var extPcs: Set<Int> = []
        for (index, pc) in chord.pcs.enumerated() where extensionIntervals.contains(chord.intervals[index]) {
            extPcs.insert(pc)
        }
        self.extensionPcs = extPcs
        let resolvedBass = enforceBass ? chord.bassPc : nil
        self.bassPc = resolvedBass
        self.allowedPcs = resolvedBass.map { cPcs.union([$0]) } ?? cPcs
        self.hasBassString = tuning.midi[0] == tuning.midi.min()
        let grips = chordForms
            .filter { $0.qualities.contains(chord.quality) }
            .compactMap { instantiateForm($0, tuning: tuning, rootPc: chord.rootPc) }
        self.standardGrips = Set(grips.map { fretsKey($0) })
    }

    func run(count: Int) -> [VoicingShape] {
        let keep = max(rankedKeep, count * 20)
        var ranked: [RankedShape] = []
        enumerate { frets in
            guard let features = describeShape(frets, tuning: tuning) else { return }
            guard isPlayable(features) else { return }
            ranked.append(RankedShape(features: features, score: score(features)))
            if ranked.count >= keep * 2 {
                ranked.sort(by: compareRanked)
                ranked.removeSubrange(keep..<ranked.count)
            }
        }
        ranked.sort(by: compareRanked)
        return pickDistinct(ranked, count: count).map(toShape)
    }

    private func stringOptions() -> [[Int?]] {
        tuning.midi.map { open in
            [nil] + (0...maxFret).compactMap { fret -> Int? in
                allowedPcs.contains(mod12(open + fret)) ? fret : nil
            }
        }
    }

    private func enumerate(_ visit: ([Int?]) -> Void) {
        let options = stringOptions()
        let n = options.count
        var reachable = [Set<Int>](repeating: [], count: n + 1)
        for s in stride(from: n - 1, through: 0, by: -1) {
            var set = reachable[s + 1]
            for fret in options[s] {
                if let fret { set.insert(mod12(tuning.midi[s] + fret)) }
            }
            reachable[s] = set
        }

        var current: [Int?] = []
        current.reserveCapacity(n)
        var counts = [Int](repeating: 0, count: 12)
        let needsDistinct = requiredPcs.count >= minDistinct
        let muteOnly: [Int?] = [nil]

        func walk(
            idx: Int,
            played: Int,
            muteRest: Bool,
            pendingMutes: Int,
            interiorMutes: Int,
            firstSounding: Int
        ) {
            for pc in requiredPcs {
                if counts[pc] == 0 && !reachable[idx].contains(pc) { return }
            }
            if played + (n - idx) < minNotes { return }
            if needsDistinct && distinctChordTones(counts) + (n - idx) < minDistinct { return }
            if idx == n {
                visit(current)
                return
            }
            let outOfReach = firstSounding >= 0 && idx - firstSounding >= maxSoundingStrings
            let choices = (muteRest || outOfReach) ? muteOnly : options[idx]
            for fret in choices {
                let interior = (fret != nil && played > 0) ? (interiorMutes + pendingMutes) : interiorMutes
                if interior > maxInteriorMutes { continue }
                current.append(fret)
                let lock = muteRest || strandsMute(current, playedBefore: played)
                if (fret == nil || !lock) && handFits(current, stringCount: n) {
                    let pc = fret.map { mod12(tuning.midi[idx] + $0) }
                    if let pc { counts[pc] += 1 }
                    let pending = fret != nil ? 0 : (played > 0 ? pendingMutes + 1 : 0)
                    let first = (firstSounding < 0 && fret != nil) ? idx : firstSounding
                    walk(
                        idx: idx + 1,
                        played: played + (pc == nil ? 0 : 1),
                        muteRest: lock,
                        pendingMutes: pending,
                        interiorMutes: interior,
                        firstSounding: first
                    )
                    if let pc { counts[pc] -= 1 }
                }
                current.removeLast()
            }
        }

        walk(idx: 0, played: 0, muteRest: false, pendingMutes: 0, interiorMutes: 0, firstSounding: -1)
    }

    private func strandsMute(_ frets: [Int?], playedBefore: Int) -> Bool {
        let last = frets.count - 1
        if last < 1 || frets[last - 1] != nil { return false }
        if playedBefore == 0 { return false }
        let below: Int? = last >= 2 ? frets[last - 2] : nil
        let above = frets[last]
        return !(below != nil && below! > 0) && !(above != nil && above! > 0)
    }

    private func distinctChordTones(_ counts: [Int]) -> Int {
        chordPcs.reduce(0) { $0 + (counts[$1] > 0 ? 1 : 0) }
    }

    private func isPlayable(_ shape: ShapeFeatures) -> Bool {
        if shape.sounding.count < minNotes { return false }
        if let bassPc, mod12(shape.bassMidi) != bassPc { return false }
        if shape.pcs.count < minDistinct && requiredPcs.count >= minDistinct { return false }
        if !requiredPcs.isSubset(of: shape.pcs) { return false }
        return mutesReachable(shape.frets) && spanReachable(shape)
    }

    private func mutesReachable(_ frets: [Int?]) -> Bool {
        let played = frets.indices.filter { frets[$0] != nil }
        guard let first = played.first, let last = played.last, played.count >= 2 else { return true }
        if first + 1 >= last { return true }
        for i in (first + 1)..<last {
            if frets[i] != nil { continue }
            let leftTouched = frets[i - 1].map { $0 > 0 } ?? false
            let rightTouched = frets[i + 1].map { $0 > 0 } ?? false
            if !leftTouched && !rightTouched { return false }
        }
        return true
    }

    private func spanReachable(_ shape: ShapeFeatures) -> Bool {
        if shape.span <= maxSpan { return true }
        guard let thumb = shape.frets[0], thumb != 0, shape.frets.count >= 4 else { return false }
        let others = shape.frets.dropFirst().compactMap { $0 }.filter { $0 > 0 }
        guard let low = others.min(), let high = others.max() else { return false }
        return high - low <= maxSpan && thumb <= low && low - thumb <= maxThumbReach
    }

    private func score(_ shape: ShapeFeatures) -> Int {
        var score = 0
        if bassPc == nil && hasBassString {
            score += (mod12(shape.bassMidi) == chord.rootPc) ? Weights.rootInBass : Weights.otherBass
        }
        score += shape.sounding.count * Weights.perNote
        score += shape.pcs.count * Weights.perDistinctPc
        score += shape.fingering.fingers * Weights.perFinger
        score += shape.fingering.barres * Weights.perBarre
        if shape.maxFret > firstPositionMaxFret {
            score += (shape.position - 1) * Weights.perPositionFret
        }
        score += shape.interiorMutes * Weights.perInteriorMute
        if shape.maxFret > openStringMaxFret {
            score += shape.openCount * Weights.perOutOfPositionOpen
        }
        score += max(0, shape.span - comfortableSpan) * Weights.perSpanFretBeyondComfort
        score += max(0, shape.range - comfortableRange) * Weights.perSemitoneBeyondRange
        score += reverseStretch(shape.frets) * Weights.perReverseStretchFret
        if extensionPcs.contains(shape.topPc) { score += Weights.extensionOnTop }
        if standardGrips.contains(fretsKey(shape.frets)) { score += Weights.standardGrip }
        return score
    }

    private func reverseStretch(_ frets: [Int?]) -> Int {
        var total = 0
        for i in 0..<max(0, frets.count - 1) {
            guard let low = frets[i], low > 0 else { continue }
            for j in (i + 1)..<min(i + 3, frets.count) {
                if let high = frets[j], high > 0, low > high + reverseStretchFrets {
                    total += low - high
                }
            }
        }
        return total
    }

    private func pickDistinct(_ ranked: [RankedShape], count: Int) -> [RankedShape] {
        var picked: [RankedShape] = []
        for entry in ranked {
            if picked.count >= count { break }
            let frets = entry.features.frets
            let duplicate = picked.contains {
                isNarrowingOf(frets, wide: $0.features.frets) || isNarrowingOf($0.features.frets, wide: frets)
            }
            if !duplicate { picked.append(entry) }
        }
        return picked
    }

    private func toShape(_ entry: RankedShape) -> VoicingShape {
        let f = entry.features
        return VoicingShape(
            frets: f.frets,
            sounding: f.sounding,
            span: f.span,
            bassMidi: f.bassMidi,
            bassIsRoot: mod12(f.bassMidi) == chord.rootPc,
            position: f.position,
            openCount: f.openCount,
            cost: entry.score
        )
    }
}

public func searchChord(
    tuning: ParsedTuning,
    chord: ParsedChord,
    count: Int = defaultVoicingCount
) -> [VoicingShape] {
    let attempts = [(true, true), (true, false), (false, false)]
    for (enforceBass, enforceExtensions) in attempts {
        if !enforceBass && chord.bassPc == nil { break }
        let shapes = VoicingSearch(
            tuning: tuning,
            chord: chord,
            enforceBass: enforceBass,
            enforceExtensions: enforceExtensions
        ).run(count: count)
        if !shapes.isEmpty { return shapes }
    }
    return []
}
