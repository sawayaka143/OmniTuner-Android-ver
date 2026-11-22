import Foundation
import Testing
@testable import OmniTunerCore

@Suite("OmniTunerCore Tests")
struct OmniTunerCoreTests {
    // MARK: - PitchEngine

    private static let rate = 48000.0
    private static let windowSize = 8192
    private static let hop = 2160

    private func pluck(fundamental: Double, seconds: Double, decayPerSecond: Double) -> [Float] {
        let n = Int(seconds * Self.rate)
        return (0..<n).map { i in
            let t = Double(i) / Self.rate
            let env = exp(-decayPerSecond * t)
            let v = 0.12 * sin(2.0 * .pi * fundamental * t)
                + 0.25 * sin(2.0 * .pi * 2.0 * fundamental * t)
                + 0.10 * sin(2.0 * .pi * 3.0 * fundamental * t)
            return Float(env * v)
        }
    }

    private func frames(for signal: [Float]) -> [[Float]] {
        var result: [[Float]] = []
        var end = Self.windowSize
        while end <= signal.count {
            result.append(Array(signal[(end - Self.windowSize)..<end]))
            end += Self.hop
        }
        return result
    }

    private func centsOff(_ f: Double, _ expected: Double) -> Double {
        abs(1200.0 * log2(f / expected))
    }

    @Test func decayingPluckStaysOnFundamental() {
        let engine = PitchEngine()
        engine.start()
        let fundamental = 82.4069
        var seen: [Double] = []
        for var frame in frames(for: pluck(fundamental: fundamental, seconds: 3.0, decayPerSecond: 1.4)) {
            _ = engine.process(window: &frame, sampleRate: Self.rate, minFrequency: PitchDetector.minFrequencyFor(lowestHz: fundamental))
            if let f = engine.frequency { seen.append(f) }
        }
        #expect(!seen.isEmpty)
        #expect(seen.allSatisfy { centsOff($0, fundamental) < 25.0 })
    }

    @Test func engineLocksOnSteadyNote() {
        let engine = PitchEngine()
        engine.start()
        let signal: [Float] = (0..<Int(Self.rate)).map {
            Float(0.2 * sin(2.0 * .pi * 196.0 * Double($0) / Self.rate))
        }
        for var frame in frames(for: signal) {
            _ = engine.process(window: &frame, sampleRate: Self.rate, minFrequency: 27.0)
        }
        #expect(engine.trackingState == .locked)
        #expect(abs((engine.frequency ?? 0) - 196.0) < 0.3)
    }

    @Test func silenceNeverLocks() {
        let engine = PitchEngine()
        engine.start()
        for _ in 0..<20 {
            var empty = [Float](repeating: 0.0, count: Self.windowSize)
            _ = engine.process(window: &empty, sampleRate: Self.rate, minFrequency: 27.0)
        }
        #expect(engine.frequency == nil)
        #expect(engine.trackingState == .listening)
    }

    @Test func stopReturnsToIdle() {
        let engine = PitchEngine()
        engine.start()
        engine.stop()
        #expect(engine.trackingState == .idle)
    }

    @Test func yinDifferenceMatchesNaiveReference() {
        let n = 1024
        let maxLag = 200
        let w = n - maxLag
        let signal: [Float] = (0..<n).map { i in
            let t = Double(i) / Self.rate
            return Float(
                0.35 * sin(2.0 * .pi * 110.0 * t + 0.2)
                    + 0.25 * sin(2.0 * .pi * 220.0 * t + 0.7)
                    + 0.12 * cos(2.0 * .pi * 330.0 * t)
            )
        }

        var actual = [Double](repeating: 0.0, count: maxLag + 1)
        let diff = YinDifference()
        diff.compute(buffer: signal, maxLag: maxLag, out: &actual)

        var maxError = 0.0
        for tau in 1...maxLag {
            var expected = 0.0
            for j in 0..<w {
                let delta = Double(signal[j]) - Double(signal[j + tau])
                expected += delta * delta
            }
            maxError = max(maxError, abs(actual[tau] - expected))
        }
        #expect(maxError < 1e-6)
    }

    // MARK: - ChordTheory

    private func parseOk(_ symbol: String) -> ParsedChord {
        switch parseChord(symbol) {
        case .ok(let chord): return chord
        case .error(_, let err): fatalError("parse failed: \(symbol): \(err)")
        }
    }

    @Test func parseNoteTokensAndTunings() {
        #expect(parseNoteToken("E2") == ParsedNote(midi: 40, pc: 4, flats: false))
        #expect(parseNoteToken("Bb3") == ParsedNote(midi: 58, pc: 10, flats: true))
        #expect(parseNoteToken("D♭3") == ParsedNote(midi: 49, pc: 1, flats: true))
        #expect(parseNoteToken("H2") == nil)

        if case .ok(let tuning) = parseTuning("E2 A2 D3 G3 B3 E4") {
            #expect(tuning.midi == [40, 45, 50, 55, 59, 64])
            #expect(tuning.flats == false)
        } else {
            Issue.record("Expected standard tuning to parse")
        }
    }

    @Test func parsesChordQualitiesAndAlterations() {
        let c = parseOk("C")
        #expect(c.rootPc == 0)
        #expect(c.quality == "maj")
        #expect(c.pcs == [0, 4, 7])

        #expect(parseOk("Cm").quality == "min")
        #expect(parseOk("Bø").quality == "m7b5")
        #expect(parseOk("CΔ7").quality == "maj7")
        #expect(parseOk("Cmaj7♯11").quality == "maj7#11")
        #expect(parseOk("C7♭9").quality == "7b9")
        #expect(parseOk("C7b5b9").intervals == [0, 4, 6, 10, 13])
    }

    // MARK: - ChordVoicing

    @Test func ranksStandardGripsNearTop() {
        guard case .ok(let standard) = parseTuning("E2 A2 D3 G3 B3 E4") else {
            Issue.record("Failed to parse standard tuning")
            return
        }
        let grips: [(String, String, Int)] = [
            ("C", "x,3,2,0,1,0", 1), ("C", "x,3,5,5,5,3", 3),
            ("D", "x,x,0,2,3,2", 1), ("D", "x,5,7,7,7,5", 2),
            ("E", "0,2,2,1,0,0", 1), ("F", "1,3,3,2,1,1", 1),
            ("G", "3,2,0,0,0,3", 1), ("A", "x,0,2,2,2,0", 1),
            ("Am", "x,0,2,2,1,0", 1), ("Em", "0,2,2,0,0,0", 1),
            ("Dm", "x,x,0,2,3,1", 1), ("C7", "x,3,2,3,1,0", 1),
            ("G7", "3,2,0,0,0,1", 1), ("Am7", "x,0,2,0,1,0", 1),
            ("Em7", "0,2,0,0,0,0", 1), ("Cmaj7", "x,3,2,0,0,0", 1),
            ("Cm9", "x,3,1,3,3,x", 1), ("B7b13", "7,x,7,8,8,x", 2),
        ]
        for (symbol, expectedFrets, maxRank) in grips {
            let shapes = searchChord(tuning: standard, chord: parseOk(symbol), count: defaultVoicingCount)
            let keys = shapes.map { $0.frets.map { $0.map(String.init) ?? "x" }.joined(separator: ",") }
            let idx = keys.firstIndex(of: expectedFrets)
            #expect(idx != nil && (idx! + 1) <= maxRank, "\(symbol) \(expectedFrets) got \(keys)")
        }
    }

    // MARK: - KeyDetector & DegreeToChord & ScaleTheory

    @Test func detectsKeysAndProgressions() {
        let chords = ["C", "G", "Am", "F"].map(parseOk)
        let key = detectKey(chords)
        #expect(key?.tonicName == "C")
        #expect(key?.mode == .ionian)
        #expect(key?.confidence == "strong")

        let degs = degreesToProgression(["I", "V", "vi", "IV"], tonicPc: 0, useFlats: false)
        #expect(degs == ["C", "G", "Am", "F"])
    }

    @Test func computesScaleFretboard() {
        let major = allScales.first { $0.id == "major" }!
        let board = computeFretboard(
            openPitchClasses: [4, 11, 7, 2, 9, 4],
            fretCount: 12,
            intervals: major.intervals,
            preferFlats: false,
            openMidiNotes: [64, 59, 55, 50, 45, 40],
            rootPitchClass: 4
        )
        #expect(board.count == 6)
        #expect(board[0].count == 13)
        #expect(board[0][0].isRoot)
    }

    // MARK: - MetronomeScheduler & Voices

    @Test func schedulerCountInAndRamp() {
        var now = 0.0
        let scheduler = MetronomeScheduler { now }
        var state = defaultMetronomeState
        state.countIn = true
        state.bpm = 60.0
        scheduler.configure(state)

        scheduler.start(now: now)
        _ = scheduler.tick(lookaheadSeconds: MetronomeScheduler.lookaheadVisibleS, now: now)
        let t0 = scheduler.getTransport(now: now)
        #expect(t0?.countIn == true)
        #expect(t0?.barIndex == -1)

        now += 4.5
        _ = scheduler.tick(lookaheadSeconds: MetronomeScheduler.lookaheadVisibleS, now: now)
        let t1 = scheduler.getTransport(now: now)
        #expect(t1?.countIn == false)
        #expect(t1?.barIndex == 0)
        scheduler.stop()
    }

    @Test func metronomeVoicesAndNoteSynthRender() {
        for option in MetronomeVoices.options {
            let pcm = MetronomeVoices.render(id: option.id, sampleRate: 48000.0, velocity: 0.8)
            #expect(!pcm.isEmpty)
        }
        let note = NoteSynth.renderNote(midi: 69, sampleRate: 48000.0)
        #expect(!note.isEmpty)
        let chime = NoteSynth.renderChime(sampleRate: 48000.0)
        #expect(!chime.isEmpty)
    }
}
