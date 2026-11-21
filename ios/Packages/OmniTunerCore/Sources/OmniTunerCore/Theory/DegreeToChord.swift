import Foundation

private let romanOffset: [String: Int] = [
    "I": 0, "II": 2, "III": 4, "IV": 5, "V": 7, "VI": 9, "VII": 11,
]

private let degreeRegex = try! NSRegularExpression(pattern: "^(b|#)?(VII|VI|IV|V|III|II|I)(.*)$", options: .caseInsensitive)
private let tonicRegex = try! NSRegularExpression(pattern: "^([A-Ga-g])\\s*([#b♯♭]?)")

public struct ParsedDegree: Sendable, Hashable {
    public let accidental: Int
    public let romanUpper: String
    public let isMinorCore: Bool
    public let suffix: String
}

public func parseDegree(_ raw: String) -> ParsedDegree? {
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    let range = NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)
    guard let m = degreeRegex.firstMatch(in: trimmed, range: range),
          let r2 = Range(m.range(at: 2), in: trimmed),
          let r3 = Range(m.range(at: 3), in: trimmed) else {
        return nil
    }
    let rawAcc = Range(m.range(at: 1), in: trimmed).map { String(trimmed[$0]) } ?? ""
    if rawAcc == "B" { return nil }
    let accidental: Int
    if rawAcc.lowercased() == "b" {
        accidental = -1
    } else if rawAcc == "#" {
        accidental = 1
    } else {
        accidental = 0
    }
    let core = String(trimmed[r2])
    let suffixRaw = String(trimmed[r3]).trimmingCharacters(in: .whitespacesAndNewlines)
    let romanUpper = core.uppercased()
    let isMinorCore = core != romanUpper
    return ParsedDegree(accidental: accidental, romanUpper: romanUpper, isMinorCore: isMinorCore, suffix: suffixRaw)
}

public func degreeToChordSymbol(_ degree: String, tonicPc: Int, useFlats: Boolean = false) -> String? {
    guard let parsed = parseDegree(degree) else { return nil }
    let offset = (romanOffset[parsed.romanUpper] ?? 0) + parsed.accidental
    let rootPc = mod12(tonicPc + offset)
    let rootName = pcName(rootPc, flats: useFlats)
    let suffixLower = parsed.suffix.lowercased()
    let suffixAlreadyHasQuality =
        suffixLower.hasPrefix("maj") ||
        suffixLower == "m" ||
        suffixLower.hasPrefix("m ") ||
        suffixLower.hasPrefix("m(") ||
        suffixLower.hasPrefix("dim") ||
        suffixLower.hasPrefix("°") ||
        suffixLower.hasPrefix("ø") ||
        suffixLower == "+" ||
        suffixLower == "aug"
    let quality: String
    if !parsed.suffix.isEmpty {
        quality = (parsed.isMinorCore && !suffixAlreadyHasQuality) ? "m\(parsed.suffix)" : parsed.suffix
    } else {
        quality = parsed.isMinorCore ? "m" : ""
    }
    return "\(rootName)\(quality)"
}

public typealias Boolean = Bool

public func degreesToProgression(_ degrees: [String], tonicPc: Int, useFlats: Bool) -> [String] {
    Array(degrees.compactMap { degreeToChordSymbol($0, tonicPc: tonicPc, useFlats: useFlats) }.prefix(6))
}

public func tonicPcOf(_ noteName: String) -> Int? {
    let trimmed = noteName.trimmingCharacters(in: .whitespacesAndNewlines)
    let range = NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)
    guard let m = tonicRegex.firstMatch(in: trimmed, range: range),
          let r1 = Range(m.range(at: 1), in: trimmed),
          var pc = pcLetter[trimmed[r1].uppercased()] else {
        return nil
    }
    let acc = Range(m.range(at: 2), in: trimmed).map { String(trimmed[$0]) } ?? ""
    if acc == "#" || acc == "♯" { pc += 1 }
    if acc == "b" || acc == "♭" { pc -= 1 }
    return mod12(pc)
}
