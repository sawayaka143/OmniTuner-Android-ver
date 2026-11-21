import Foundation

public let sharpPcNames: [String] = [
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
]

public let flatPcNames: [String] = [
    "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
]

public let degreeLabels: [String] = [
    "R", "b2", "2", "b3", "3", "4", "b5", "5", "#5", "6", "b7", "7",
]

public let pcLetter: [String: Int] = [
    "C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11,
]

public struct ChordFormula: Sendable, Hashable {
    public let intervals: [Int]
    public let optional: [Int]

    public init(_ intervals: [Int], _ optional: [Int] = []) {
        self.intervals = intervals
        self.optional = optional
    }
}

public let chordFormulas: [String: ChordFormula] = [
    "maj": ChordFormula([0, 4, 7]),
    "6": ChordFormula([0, 4, 7, 9]),
    "6/9": ChordFormula([0, 4, 7, 9, 14]),
    "maj7": ChordFormula([0, 4, 7, 11]),
    "maj9": ChordFormula([0, 4, 7, 11, 14]),
    "maj11": ChordFormula([0, 4, 7, 11, 14, 17], [17]),
    "maj13": ChordFormula([0, 4, 7, 11, 14, 21], [21]),
    "maj7#11": ChordFormula([0, 4, 7, 11, 18], [18]),
    "maj13#11": ChordFormula([0, 4, 7, 11, 14, 18, 21], [18, 21]),
    "maj7#5": ChordFormula([0, 4, 8, 11]),
    "maj9#5": ChordFormula([0, 4, 8, 11, 14]),

    "7": ChordFormula([0, 4, 7, 10]),
    "9": ChordFormula([0, 4, 7, 10, 14]),
    "11": ChordFormula([0, 4, 7, 10, 14, 17], [17]),
    "13": ChordFormula([0, 4, 7, 10, 14, 21], [21]),
    "7b5": ChordFormula([0, 4, 6, 10]),
    "7#5": ChordFormula([0, 4, 8, 10]),
    "7b9": ChordFormula([0, 4, 7, 10, 13]),
    "7#9": ChordFormula([0, 4, 7, 10, 15]),
    "7#11": ChordFormula([0, 4, 7, 10, 18], [18]),
    "7b13": ChordFormula([0, 4, 7, 10, 20], [20]),
    "9#11": ChordFormula([0, 4, 7, 10, 14, 18], [18]),
    "13b9": ChordFormula([0, 4, 7, 10, 13, 21], [21]),
    "7#9b13": ChordFormula([0, 4, 7, 10, 15, 20], [20]),
    "7alt": ChordFormula([0, 4, 10, 15, 20]),

    "min": ChordFormula([0, 3, 7]),
    "m6": ChordFormula([0, 3, 7, 9]),
    "m6/9": ChordFormula([0, 3, 7, 9, 14]),
    "m7": ChordFormula([0, 3, 7, 10]),
    "m9": ChordFormula([0, 3, 7, 10, 14]),
    "m11": ChordFormula([0, 3, 7, 10, 14, 17], [17]),
    "m13": ChordFormula([0, 3, 7, 10, 14, 17, 21], [17, 21]),
    "mMaj7": ChordFormula([0, 3, 7, 11]),
    "mMaj9": ChordFormula([0, 3, 7, 11, 14]),
    "mMaj11": ChordFormula([0, 3, 7, 11, 14, 17], [17]),
    "mMaj13": ChordFormula([0, 3, 7, 11, 14, 17, 21], [17, 21]),
    "m7b5": ChordFormula([0, 3, 6, 10]),

    "dim": ChordFormula([0, 3, 6]),
    "dim7": ChordFormula([0, 3, 6, 9]),
    "ø9": ChordFormula([0, 3, 6, 10, 14]),
    "aug": ChordFormula([0, 4, 8]),
    "9#5": ChordFormula([0, 4, 8, 10, 14]),
    "sus2": ChordFormula([0, 2, 7]),
    "sus4": ChordFormula([0, 5, 7]),
    "7sus2": ChordFormula([0, 2, 7, 10]),
    "7sus4": ChordFormula([0, 5, 7, 10]),
    "maj7sus4": ChordFormula([0, 5, 7, 11]),
    "9sus4": ChordFormula([0, 5, 7, 10, 14]),
    "13sus4": ChordFormula([0, 5, 7, 10, 14, 21], [21]),
    "6sus4": ChordFormula([0, 5, 7, 9]),
    "add9": ChordFormula([0, 4, 7, 14]),
    "add11": ChordFormula([0, 4, 7, 17]),
    "madd9": ChordFormula([0, 3, 7, 14]),
    "madd11": ChordFormula([0, 3, 7, 17]),
    "5": ChordFormula([0, 7]),
]

public let qualityAliases: [String: String] = [
    "": "maj",
    "maj": "maj",
    "M": "maj",
    "major": "maj",
    "m": "min",
    "min": "min",
    "mi": "min",
    "-": "min",
    "7": "7",
    "dom7": "7",
    "maj7": "maj7",
    "M7": "maj7",
    "Δ7": "maj7",
    "delta7": "maj7",
    "m7": "m7",
    "mMaj7": "mMaj7",
    "m(maj7)": "mMaj7",
    "mmaj": "mMaj7",
    "m(maj)": "mMaj7",
    "mM7": "mMaj7",
    "mΔ7": "mMaj7",
    "dim": "dim",
    "°": "dim",
    "o": "dim",
    "dim7": "dim7",
    "°7": "dim7",
    "o7": "dim7",
    "m7b5": "m7b5",
    "ø": "m7b5",
    "ø7": "m7b5",
    "halfdim": "m7b5",
    "aug": "aug",
    "+": "aug",
    "sus2": "sus2",
    "sus4": "sus4",
    "sus": "sus4",
    "6": "6",
    "add6": "6",
    "m6": "m6",
    "9": "9",
    "m9": "m9",
    "add9": "add9",
    "add2": "add9",
    "5": "5",
    "pow": "5",

    "maj9": "maj9",
    "M9": "maj9",
    "Δ9": "maj9",
    "delta9": "maj9",
    "maj11": "maj11",
    "M11": "maj11",
    "Δ11": "maj11",
    "delta11": "maj11",
    "maj13": "maj13",
    "M13": "maj13",
    "Δ13": "maj13",
    "delta13": "maj13",
    "maj7#11": "maj7#11",
    "M7#11": "maj7#11",
    "Δ7#11": "maj7#11",
    "maj13#11": "maj13#11",
    "M13#11": "maj13#11",
    "Δ13#11": "maj13#11",
    "maj7#5": "maj7#5",
    "M7#5": "maj7#5",
    "Δ7#5": "maj7#5",
    "maj7+5": "maj7#5",
    "maj9#5": "maj9#5",
    "M9#5": "maj9#5",
    "Δ9#5": "maj9#5",
    "9+5": "9#5",
    "aug9": "9#5",
    "+9": "9#5",
    "maj6": "6",
    "6/9": "6/9",
    "69": "6/9",
    "6add9": "6/9",
    "m6/9": "m6/9",
    "m69": "m6/9",
    "m6add9": "m6/9",

    "11": "11",
    "13": "13",
    "7b5": "7b5",
    "7#5": "7#5",
    "+7": "7#5",
    "aug7": "7#5",
    "7b9": "7b9",
    "7#9": "7#9",
    "7#11": "7#11",
    "7b13": "7b13",
    "9#11": "9#11",
    "13b9": "13b9",
    "7#9b13": "7#9b13",
    "7alt": "7alt",
    "alt": "7alt",

    "m11": "m11",
    "m13": "m13",
    "mMaj9": "mMaj9",
    "mM9": "mMaj9",
    "m(maj9)": "mMaj9",
    "mΔ9": "mMaj9",
    "m(M9)": "mMaj9",
    "mMaj11": "mMaj11",
    "mM11": "mMaj11",
    "m(maj11)": "mMaj11",
    "mΔ11": "mMaj11",
    "mMaj13": "mMaj13",
    "mM13": "mMaj13",
    "m(maj13)": "mMaj13",
    "mΔ13": "mMaj13",

    "ø9": "ø9",
    "halfdim9": "ø9",
    "7sus2": "7sus2",
    "7sus": "7sus4",
    "7sus4": "7sus4",
    "maj7sus4": "maj7sus4",
    "M7sus4": "maj7sus4",
    "Δ7sus4": "maj7sus4",
    "9sus4": "9sus4",
    "9sus": "9sus4",
    "13sus4": "13sus4",
    "13sus": "13sus4",
    "6sus4": "6sus4",
    "add11": "add11",
    "add4": "add11",
    "madd9": "madd9",
    "madd2": "madd9",
    "madd11": "madd11",
    "madd4": "madd11",
    "+maj7": "maj7#5",
    "augmaj7": "maj7#5",
    "Δ": "maj7",
]

public enum ModeName: String, CaseIterable, Sendable, Hashable {
    case ionian = "Ionian"
    case dorian = "Dorian"
    case phrygian = "Phrygian"
    case lydian = "Lydian"
    case mixolydian = "Mixolydian"
    case aeolian = "Aeolian"
    case locrian = "Locrian"

    public var steps: [Int] {
        switch self {
        case .ionian: return [0, 2, 4, 5, 7, 9, 11]
        case .dorian: return [0, 2, 3, 5, 7, 9, 10]
        case .phrygian: return [0, 1, 3, 5, 7, 8, 10]
        case .lydian: return [0, 2, 4, 6, 7, 9, 11]
        case .mixolydian: return [0, 2, 4, 5, 7, 9, 10]
        case .aeolian: return [0, 2, 3, 5, 7, 8, 10]
        case .locrian: return [0, 1, 3, 5, 6, 8, 10]
        }
    }

    public var ordinal: Int {
        switch self {
        case .ionian: return 0
        case .dorian: return 1
        case .phrygian: return 2
        case .lydian: return 3
        case .mixolydian: return 4
        case .aeolian: return 5
        case .locrian: return 6
        }
    }
}

public let modeNames: [ModeName] = ModeName.allCases

public func mod12(_ value: Int) -> Int {
    ((value % 12) + 12) % 12
}

public func flatsForPc(_ pc: Int) -> Bool {
    [1, 3, 6, 8, 10].contains(mod12(pc))
}

public func pcName(_ pc: Int, flats: Bool) -> String {
    (flats ? flatPcNames : sharpPcNames)[mod12(pc)]
}

public func midiName(_ midi: Int, flats: Bool) -> String {
    "\(pcName(midi, flats: flats))\(Int(floor(Double(midi) / 12.0)) - 1)"
}

public struct ParsedNote: Sendable, Hashable {
    public let midi: Int
    public let pc: Int
    public let flats: Bool
}

private let noteTokenRegex = try! NSRegularExpression(pattern: "^([A-Ga-g])\\s*([#b♯♭]?)([-+]?\\d+)$")

public func parseNoteToken(_ token: String) -> ParsedNote? {
    let trimmed = token.trimmingCharacters(in: .whitespacesAndNewlines)
    let range = NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)
    guard let match = noteTokenRegex.firstMatch(in: trimmed, range: range),
          let letterRange = Range(match.range(at: 1), in: trimmed),
          let accRange = Range(match.range(at: 2), in: trimmed),
          let octRange = Range(match.range(at: 3), in: trimmed),
          var pc = pcLetter[trimmed[letterRange].uppercased()],
          let octave = Int(trimmed[octRange]) else {
        return nil
    }
    let accidental = String(trimmed[accRange])
    if accidental == "#" || accidental == "♯" { pc += 1 }
    if accidental == "b" || accidental == "♭" { pc -= 1 }
    let midi = (octave + 1) * 12 + pc
    guard midi >= 0 && midi <= 127 else { return nil }
    return ParsedNote(midi: midi, pc: mod12(pc), flats: accidental == "b" || accidental == "♭")
}

public struct ParsedTuning: Sendable, Hashable {
    public let midi: [Int]
    public let labels: [String]
    public let flats: Bool
}

public enum TuningParseResult: Sendable, Hashable {
    case ok(ParsedTuning)
    case error(String)
}

public func parseTuning(_ raw: String) -> TuningParseResult {
    let tokens = raw.components(separatedBy: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ",")))
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
    if tokens.isEmpty { return .error("empty tuning") }
    if tokens.count > 12 { return .error("max 12 strings") }
    var midi: [Int] = []
    var labels: [String] = []
    var parsedNotes: [ParsedNote] = []
    for token in tokens {
        guard let parsed = parseNoteToken(token) else {
            return .error("bad note '\(token)'")
        }
        midi.append(parsed.midi)
        labels.append(midiName(parsed.midi, flats: parsed.flats))
        parsedNotes.append(parsed)
    }
    let flatCount = parsedNotes.filter(\.flats).count
    let flats = Double(flatCount) > Double(parsedNotes.count) / 2.0
    return .ok(ParsedTuning(midi: midi, labels: labels, flats: flats))
}

public struct ParsedChord: Sendable, Hashable {
    public let symbol: String
    public let rootPc: Int
    public let quality: String
    public let intervals: [Int]
    public let pcs: [Int]
    public let optionalPcs: [Int]
    public let flats: Bool
    public let bassPc: Int?
}

public enum ChordParseResult: Sendable, Hashable {
    case ok(ParsedChord)
    case error(symbol: String, error: String)
}

private func stripParens(_ raw: String) -> String? {
    var depth = 0
    var out = ""
    for ch in raw {
        if ch == "(" {
            depth += 1
            continue
        }
        if ch == ")" {
            depth -= 1
            if depth < 0 { return nil }
            continue
        }
        if depth > 0 && (ch == "," || ch.isWhitespace) { continue }
        out.append(ch)
    }
    return depth == 0 ? out : nil
}

private let majorMRegex = try! NSRegularExpression(pattern: "M(?![aAiI])")
private let bareDeltaRegex = try! NSRegularExpression(pattern: "(?:Δ|delta)(?!\\d)")
private let deltaRegex = try! NSRegularExpression(pattern: "Δ|delta")
private let minorWordRegex = try! NSRegularExpression(pattern: "^min?")
private let minusMinorRegex = try! NSRegularExpression(pattern: "^-(?!5)")
private let sevenMajorRegex = try! NSRegularExpression(pattern: "^(m?)7maj")

private func replaceRegex(_ regex: NSRegularExpression, in text: String, with template: String) -> String {
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.stringByReplacingMatches(in: text, range: range, withTemplate: template)
}

private func canonicalQuality(_ raw: String) -> String? {
    let replaced = raw.replacingOccurrences(of: "♯", with: "#").replacingOccurrences(of: "♭", with: "b")
    guard var text = stripParens(replaced) else { return nil }
    text = replaceRegex(bareDeltaRegex, in: text, with: "maj7")
    text = replaceRegex(deltaRegex, in: text, with: "maj")
    text = replaceRegex(majorMRegex, in: text, with: "maj").lowercased()
    text = replaceRegex(minorWordRegex, in: text, with: "m")
    text = replaceRegex(minusMinorRegex, in: text, with: "m")
    return replaceRegex(sevenMajorRegex, in: text, with: "$1maj7")
}

private let canonicalAliases: [String: String] = {
    var map: [String: String] = [:]
    for (key, quality) in qualityAliases {
        if let canon = canonicalQuality(key) {
            map[canon] = quality
        }
    }
    return map
}()

private let alterationRegex = try! NSRegularExpression(
    pattern: "(?:b|#)?(?:5|9|11|13)|[b#]6|add(?:[b#]?(?:9|11|13)|2|4|6)|sus(?:2|4)?|[+-]5"
)

private let canonicalTokens: [String: String] = [
    "+5": "#5", "-5": "b5", "sus": "sus4", "add2": "add9", "add4": "add11",
]

private struct ComposedQuality {
    let key: String
    let intervals: [Int]
    let optional: [Int]
}

private func composeQuality(_ raw: String) -> ComposedQuality? {
    guard let normalized = canonicalQuality(raw), !normalized.isEmpty else { return nil }

    var baseKey: String? = nil
    var prefixLength = 0
    for key in chordFormulas.keys {
        let lowerKey = key.lowercased()
        if normalized.hasPrefix(lowerKey) && (baseKey == nil || key.count > baseKey!.count) {
            baseKey = key
            prefixLength = lowerKey.count
        }
    }
    if baseKey == nil {
        if normalized.hasPrefix("m") {
            baseKey = "min"
            prefixLength = 1
        } else if normalized.hasPrefix("sus") {
            baseKey = "sus4"
            prefixLength = 3
        } else {
            baseKey = "maj"
            prefixLength = 0
        }
    }

    guard let resolvedBaseKey = baseKey, let base = chordFormulas[resolvedBaseKey] else { return nil }
    let remainder = String(normalized.dropFirst(prefixLength))
    var intervals = base.intervals
    var optional = base.optional
    var tokens: [String] = []

    let nsRemainder = remainder as NSString
    let matches = alterationRegex.matches(in: remainder, range: NSRange(location: 0, length: nsRemainder.length))
    var cursor = 0
    for match in matches {
        if match.range.location != cursor { return nil }
        tokens.append(nsRemainder.substring(with: match.range))
        cursor = match.range.location + match.range.length
    }
    if cursor != nsRemainder.length { return nil }

    func drop(_ value: Int) {
        intervals.removeAll { $0 == value }
    }
    func removeOptional(_ value: Int) {
        optional.removeAll { $0 == value }
    }
    func add(_ value: Int) {
        if !intervals.contains(value) { intervals.append(value) }
    }
    func addOptional(_ value: Int) {
        add(value)
        if !optional.contains(value) { optional.append(value) }
    }

    if resolvedBaseKey == "aug", let first = tokens.first, ["9", "11", "13"].contains(first) {
        intervals.append(10)
        if first != "9" { intervals.append(14) }
    }

    for token in tokens {
        switch token {
        case "b5", "-5":
            drop(7); add(6); removeOptional(6)
        case "#5", "+5":
            drop(7); add(8); removeOptional(8)
        case "b9":
            drop(14); add(13)
        case "#9":
            drop(14); add(15)
        case "9":
            drop(13); drop(15); add(14)
        case "11":
            drop(18); removeOptional(18); addOptional(17)
        case "#11":
            drop(17); removeOptional(17); addOptional(18)
        case "13":
            drop(20); removeOptional(20); addOptional(21)
        case "b13":
            drop(21); removeOptional(21); addOptional(20)
        case "b6":
            add(8)
        case "#6":
            add(10)
        case "sus2":
            drop(3); drop(4); add(2)
        case "sus4", "sus":
            drop(3); drop(4); add(5)
        case "add9", "add2":
            add(14)
        case "addb9":
            add(13)
        case "add#9":
            add(15)
        case "add11", "add4":
            addOptional(17)
        case "add#11":
            add(18)
        case "add6":
            add(9)
        case "add13":
            add(21)
        case "addb13":
            add(20)
        default:
            break
        }
    }

    var distinctIntervals: [Int] = []
    for v in intervals where !distinctIntervals.contains(v) { distinctIntervals.append(v) }
    distinctIntervals.sort()
    var distinctOptional: [Int] = []
    for v in optional where !distinctOptional.contains(v) { distinctOptional.append(v) }
    distinctOptional.sort()

    let suffix = tokens.map { canonicalTokens[$0] ?? $0 }.joined()
    return ComposedQuality(key: resolvedBaseKey + suffix, intervals: distinctIntervals, optional: distinctOptional)
}

private let rootRegex = try! NSRegularExpression(pattern: "^([A-Ga-g])\\s*([#b♯♭]?)\\s*(.*?)\\s*$")
private let bassNoteRegex = try! NSRegularExpression(pattern: "^\\s*([A-Ga-g])\\s*([#b♯♭]?)\\s*$")

private func pcOf(letter: String, accidental: String) -> Int {
    var pc = pcLetter[letter.uppercased()] ?? 0
    if accidental == "#" || accidental == "♯" { pc += 1 }
    if accidental == "b" || accidental == "♭" { pc -= 1 }
    return mod12(pc)
}

private func isFlat(_ accidental: String) -> Bool {
    accidental == "b" || accidental == "♭"
}

public func parseChord(_ raw: String) -> ChordParseResult {
    let symbol = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    var bassPc: Int? = nil
    var bassFlats = false
    var chordPart = symbol

    if let slashIdx = symbol.lastIndex(of: "/") {
        let afterSlash = String(symbol[symbol.index(after: slashIdx)...])
        let range = NSRange(afterSlash.startIndex..<afterSlash.endIndex, in: afterSlash)
        if let m = bassNoteRegex.firstMatch(in: afterSlash, range: range),
           let r1 = Range(m.range(at: 1), in: afterSlash),
           let r2 = Range(m.range(at: 2), in: afterSlash) {
            let letter = String(afterSlash[r1])
            let acc = String(afterSlash[r2])
            bassPc = pcOf(letter: letter, accidental: acc)
            bassFlats = isFlat(acc)
            chordPart = String(symbol[..<slashIdx])
        }
    }

    let chordRange = NSRange(chordPart.startIndex..<chordPart.endIndex, in: chordPart)
    guard let match = rootRegex.firstMatch(in: chordPart, range: chordRange),
          let r1 = Range(match.range(at: 1), in: chordPart),
          let r2 = Range(match.range(at: 2), in: chordPart),
          let r3 = Range(match.range(at: 3), in: chordPart) else {
        return .error(symbol: symbol, error: "'\(symbol)' is not a chord symbol")
    }

    let letter = String(chordPart[r1])
    let acc = String(chordPart[r2])
    let qualityRaw = String(chordPart[r3])
    let rootPc = pcOf(letter: letter, accidental: acc)

    var quality: String? = canonicalQuality(qualityRaw).flatMap { canonicalAliases[$0] }
    var formula: ChordFormula? = quality.flatMap { chordFormulas[$0] }
    if formula == nil, let composed = composeQuality(qualityRaw) {
        quality = composed.key
        formula = ChordFormula(composed.intervals, composed.optional)
    }

    guard let resolvedQuality = quality, let resolvedFormula = formula else {
        let shown = qualityRaw.isEmpty ? "(none)" : qualityRaw
        return .error(symbol: symbol, error: "unknown chord quality '\(shown)' in '\(symbol)'")
    }

    let intervals = resolvedFormula.intervals
    return .ok(
        ParsedChord(
            symbol: symbol,
            rootPc: rootPc,
            quality: resolvedQuality,
            intervals: intervals,
            pcs: intervals.map { mod12(rootPc + $0) },
            optionalPcs: resolvedFormula.optional.map { mod12(rootPc + $0) },
            flats: isFlat(acc) || bassFlats,
            bassPc: bassPc
        )
    )
}

private let strongSeparators: Set<Character> = [",", ";", "|", "→", "—", "–"]
private let noteOrEnd = "(?=$|[\\s,;|→—–]|->)"
private let bassSlashRegex = try! NSRegularExpression(pattern: "^\\s*[A-Ga-g][#b♯♭]?\(noteOrEnd)")
private let bassSlashSpacesRegex = try! NSRegularExpression(pattern: "\\s*/\\s*(?=[A-Ga-g][#b♯♭]?\(noteOrEnd))")
private let digitRegex = try! NSRegularExpression(pattern: "^\\s*\\d")

private func matchesPrefix(_ regex: NSRegularExpression, in text: String) -> Bool {
    let range = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.firstMatch(in: text, range: range) != nil
}

public func tokenizeProgression(_ raw: String) -> [String] {
    let text = replaceRegex(bassSlashSpacesRegex, in: raw, with: "/")
    let chars = Array(text)
    var tokens: [String] = []
    let balanced = chars.filter { $0 == "(" }.count == chars.filter { $0 == ")" }.count
    var current = ""
    var depth = 0

    func flush() {
        let token = current.trimmingCharacters(in: .whitespacesAndNewlines)
        if !token.isEmpty && token != "/" {
            tokens.append(token)
        }
        current = ""
    }

    var i = 0
    while i < chars.count {
        let ch = chars[i]
        if balanced && ch == "(" {
            depth += 1
            current.append(ch)
        } else if balanced && ch == ")" {
            depth = max(0, depth - 1)
            current.append(ch)
        } else if depth > 0 {
            current.append(ch)
        } else if strongSeparators.contains(ch) {
            flush()
        } else if ch == "-" && i + 1 < chars.count && chars[i + 1] == ">" {
            flush()
            i += 1
        } else if ch == "/" {
            let rest = String(chars[(i + 1)...])
            if matchesPrefix(bassSlashRegex, in: rest) || matchesPrefix(digitRegex, in: rest) {
                current.append("/")
            } else {
                flush()
            }
        } else if ch.isWhitespace {
            flush()
        } else {
            current.append(ch)
        }
        i += 1
    }
    flush()
    return tokens
}

public enum BadgeKind: Sendable, Hashable {
    case good, warn, borrowed, bad
}

public struct DiatonicBadge: Sendable, Hashable {
    public let kind: BadgeKind
    public let numeral: String
    public let detail: String
    public let harmonicMinor: Bool

    public init(kind: BadgeKind, numeral: String, detail: String, harmonicMinor: Bool = false) {
        self.kind = kind
        self.numeral = numeral
        self.detail = detail
        self.harmonicMinor = harmonicMinor
    }
}

public func keyName(tonicName: String, mode: ModeName) -> String {
    switch mode {
    case .ionian: return "\(tonicName) major"
    case .aeolian: return "\(tonicName) minor"
    default: return "\(tonicName) \(mode.rawValue)"
    }
}

private let expectedQualityMap: [String: String] = [
    "3,6": "dim",
    "3,7": "min",
    "4,7": "maj",
    "4,8": "aug",
]

private let qualityWord: [String: String] = [
    "maj": "major",
    "min": "minor",
    "dim": "diminished",
    "aug": "augmented",
]

private let numerals = ["I", "II", "III", "IV", "V", "VI", "VII"]
private let harmonicMinorSteps = [0, 2, 3, 5, 7, 8, 11]
private let harmonicDominant = 4

private struct DegreeLookup {
    let degreeIndex: Int
    let third: Int
    let fifth: Int
    let expectedQuality: String
    let numeral: String
}

private func numeralFor(degreeIndex: Int, expectedQuality: String, steps: [Int]) -> String {
    let base = numerals[degreeIndex]
    let lower = (expectedQuality == "min" || expectedQuality == "dim") ? base.lowercased() : base
    let suffix: String
    switch expectedQuality {
    case "dim": suffix = "°"
    case "aug": suffix = "+"
    default: suffix = ""
    }
    let prefix: String
    switch mod12(steps[degreeIndex] - ModeName.ionian.steps[degreeIndex]) {
    case 11: prefix = "b"
    case 1: prefix = "#"
    default: prefix = ""
    }
    return "\(prefix)\(lower)\(suffix)"
}

private func lookupIn(rootPc: Int, tonicPc: Int, steps: [Int]) -> DegreeLookup? {
    guard let degreeIndex = (0..<7).first(where: { mod12(tonicPc + steps[$0]) == rootPc }) else {
        return nil
    }
    let third = mod12(steps[(degreeIndex + 2) % 7] - steps[degreeIndex])
    let fifth = mod12(steps[(degreeIndex + 4) % 7] - steps[degreeIndex])
    let expectedQuality = expectedQualityMap["\(third),\(fifth)"] ?? "maj"
    return DegreeLookup(
        degreeIndex: degreeIndex,
        third: third,
        fifth: fifth,
        expectedQuality: expectedQuality,
        numeral: numeralFor(degreeIndex: degreeIndex, expectedQuality: expectedQuality, steps: steps)
    )
}

public func computeBadgeForPc(
    chord: ParsedChord,
    tonicPc: Int,
    modeName: ModeName,
    tuningFlats: Bool,
    tonicFlats: Bool
) -> DiatonicBadge {
    let scaleRootName = pcName(tonicPc, flats: tonicFlats || tuningFlats)
    let key = keyName(tonicName: scaleRootName, mode: modeName)
    let rootName = pcName(chord.rootPc, flats: chord.flats || tuningFlats)

    let actualThird: Int? = chord.intervals.contains(4) ? 4 : (chord.intervals.contains(3) ? 3 : nil)
    let actualFifth: Int? = [7, 6, 8].first { chord.intervals.contains($0) }

    func qualityMatches(_ lookup: DegreeLookup) -> Bool {
        (actualThird == nil || actualThird == lookup.third) &&
            (actualFifth == nil || actualFifth == lookup.fifth)
    }

    let primary = lookupIn(rootPc: chord.rootPc, tonicPc: tonicPc, steps: modeName.steps)
    if let primary, qualityMatches(primary) {
        return DiatonicBadge(kind: .good, numeral: primary.numeral, detail: "diatonic to \(key)")
    }

    if modeName == .aeolian {
        if let harmonic = lookupIn(rootPc: chord.rootPc, tonicPc: tonicPc, steps: harmonicMinorSteps),
           harmonic.degreeIndex == harmonicDominant,
           qualityMatches(harmonic) {
            return DiatonicBadge(
                kind: .good,
                numeral: harmonic.numeral,
                detail: "from \(scaleRootName) harmonic minor",
                harmonicMinor: true
            )
        }
    }

    if let primary {
        let word = qualityWord[primary.expectedQuality] ?? primary.expectedQuality
        return DiatonicBadge(
            kind: .warn,
            numeral: primary.numeral,
            detail: "altered: \(key) expects \(word) here"
        )
    }

    for (steps, label) in [(ModeName.ionian.steps, "major"), (ModeName.aeolian.steps, "minor")] {
        if let lookup = lookupIn(rootPc: chord.rootPc, tonicPc: tonicPc, steps: steps), qualityMatches(lookup) {
            return DiatonicBadge(
                kind: .borrowed,
                numeral: lookup.numeral,
                detail: "borrowed from \(scaleRootName) \(label)"
            )
        }
    }

    return DiatonicBadge(kind: .bad, numeral: "—", detail: "chromatic: \(rootName) isn't in \(key)")
}
