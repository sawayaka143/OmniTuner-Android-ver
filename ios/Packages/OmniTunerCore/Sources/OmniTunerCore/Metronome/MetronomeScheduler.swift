import Foundation

public struct ScheduledSound: Sendable, Hashable {
    public let time: Double
    public let role: String
    public let layer: String
    public let id: String
    public let vol: Double
}

public struct TransportSnapshot: Sendable, Hashable {
    public let progress: Double
    public let barIndex: Int
    public let patternPos: Int
    public let beatsPerBar: Int
    public let pulsesPerBar: Int
    public let pulsesPerBeat: Int
    public let barActive: Bool
    public let countIn: Bool
    public let bpm: Int
}

public final class MetronomeScheduler {
    public static let tickMs: Int64 = 25
    public static let lookaheadVisibleS = 0.12
    public static let lookaheadHiddenS = 1.1
    public static let startDelayS = 0.08

    private let clock: () -> Double

    public private(set) var config: MetronomeState = defaultMetronomeState
    public private(set) var isPlaying = false

    private var dirty = false
    private var bpmPending = false
    private var barCount = 0
    private var patternPos = 0
    private var barBase = 0.0
    private var anchorT = 0.0
    private var anchorB = 0.0
    private var spb: Double = 60.0 / defaultMetronomeState.bpm
    private var rampStartBpm: Double = defaultMetronomeState.bpm
    private var inCountIn = false
    private var barEvents: [BarEvent] = []
    private var nextIdx = 0
    private var model: MeterModel = meterModel(
        numerator: defaultMetronomeState.timeSignature.numerator,
        denominator: defaultMetronomeState.timeSignature.denominator
    )

    public init(clock: @escaping () -> Double) {
        self.clock = clock
    }

    public func configure(_ state: MetronomeState) {
        if state.bpm != config.bpm {
            config.bpm = state.bpm
            if isPlaying { bpmPending = true } else { spb = 60.0 / state.bpm }
        }
        if state.masterVol != config.masterVol {
            config.masterVol = state.masterVol
        }
        if state.sounds != config.sounds {
            config.sounds = state.sounds
        }
        if state.timeSignature != config.timeSignature ||
            state.divisionsPerBeat != config.divisionsPerBeat ||
            state.barPattern != config.barPattern ||
            state.poly != config.poly {
            config.timeSignature = state.timeSignature
            config.divisionsPerBeat = state.divisionsPerBeat
            config.barPattern = state.barPattern
            config.poly = state.poly
            dirty = true
        }
        if state.countIn != config.countIn {
            config.countIn = state.countIn
        }
        if state.ramp != config.ramp {
            config.ramp = state.ramp
        }
    }

    public func start(now: Double? = nil) {
        let now = now ?? clock()
        if isPlaying { return }
        dirty = false
        bpmPending = false
        model = meterModel(numerator: config.timeSignature.numerator, denominator: config.timeSignature.denominator)
        spb = 60.0 / config.bpm
        rampStartBpm = config.bpm
        inCountIn = config.countIn
        barCount = inCountIn ? -1 : 0
        patternPos = 0
        barBase = inCountIn ? -Double(model.beatsPerBar) : 0.0
        anchorB = barBase
        anchorT = now + Self.startDelayS
        buildBar()
        nextIdx = 0
        isPlaying = true
    }

    public func stop() {
        if !isPlaying { return }
        isPlaying = false
        inCountIn = false
    }

    public func getTransport(now: Double? = nil) -> TransportSnapshot? {
        guard isPlaying else { return nil }
        let now = now ?? clock()
        let barStart = anchorT + (barBase - anchorB) * spb
        let dur = Double(model.beatsPerBar) * spb
        let progress = min(1.0, max(0.0, (now - barStart) / dur))
        let barActive = barCount < 0 || (patternPos < config.barPattern.count && config.barPattern[patternPos] == 1)
        return TransportSnapshot(
            progress: progress,
            barIndex: barCount,
            patternPos: patternPos,
            beatsPerBar: model.beatsPerBar,
            pulsesPerBar: model.numerator,
            pulsesPerBeat: model.divisionsPerBeat,
            barActive: barActive,
            countIn: barCount < 0,
            bpm: Int(60.0 / spb)
        )
    }

    public func tick(lookaheadSeconds: Double, now: Double? = nil) -> [ScheduledSound] {
        guard isPlaying else { return [] }
        let now = now ?? clock()
        let horizon = now + lookaheadSeconds
        var due: [ScheduledSound] = []
        var guardCount = 0
        while guardCount < 512 {
            guardCount += 1
            if nextIdx >= barEvents.count {
                let endBeat = barBase + Double(model.beatsPerBar)
                if timeOf(endBeat) > horizon { break }
                advanceBar()
                continue
            }
            let event = barEvents[nextIdx]
            let t = timeOf(barBase + event.beats)
            if t > horizon { break }
            nextIdx += 1
            if t >= now - 0.01 {
                if let spec = soundFor(event), spec.vol > 0.002 {
                    due.append(
                        ScheduledSound(
                            time: max(t, now + 0.001),
                            role: event.role,
                            layer: event.layer,
                            id: spec.id,
                            vol: spec.vol
                        )
                    )
                }
            }
        }
        return due
    }

    private func timeOf(_ beat: Double) -> Double {
        anchorT + (beat - anchorB) * spb
    }

    private func setSpbAt(bpm: Double, atBeat: Double) {
        guard bpm.isFinite, bpm > 0 else { return }
        let t = anchorT + (atBeat - anchorB) * spb
        anchorT = t
        anchorB = atBeat
        spb = 60.0 / bpm
    }

    private func applyRampStep() {
        guard config.ramp.enabled, !inCountIn, barCount >= 0 else { return }
        let bars = config.ramp.bars
        let targetBpm = config.ramp.targetBpm
        let progress = min(1.0, max(0.0, Double(barCount) / Double(max(1, bars))))
        let effBpm = rampStartBpm + (targetBpm - rampStartBpm) * progress
        setSpbAt(bpm: effBpm, atBeat: barBase)
    }

    private func advanceBar() {
        let finishedBeats = model.beatsPerBar
        if dirty {
            dirty = false
            model = meterModel(numerator: config.timeSignature.numerator, denominator: config.timeSignature.denominator)
        }
        barBase += Double(finishedBeats)
        barCount += 1
        if inCountIn {
            inCountIn = false
            patternPos = 0
        } else {
            patternPos = (patternPos + 1) % max(1, config.barPattern.count)
        }
        if bpmPending {
            bpmPending = false
            rampStartBpm = config.bpm
            setSpbAt(bpm: config.bpm, atBeat: barBase)
        }
        applyRampStep()
        buildBar()
        nextIdx = 0
    }

    private func buildBar() {
        let countInBar = inCountIn
        let active = countInBar || (patternPos < config.barPattern.count && config.barPattern[patternPos] == 1)
        if active {
            if countInBar {
                barEvents = buildCountInEvents()
            } else {
                barEvents = buildBarEvents(
                    model: model,
                    subdivision: config.divisionsPerBeat,
                    poly: PolyEvents(
                        enabled: config.poly.enabled,
                        events: config.poly.events,
                        accentFirst: config.poly.accentFirst
                    )
                )
            }
        } else {
            barEvents = []
        }
    }

    private func buildCountInEvents() -> [BarEvent] {
        (0..<model.beatsPerBar).map { b in
            BarEvent(beats: Double(b), layer: "meter", role: b == 0 ? "downbeat" : "beat")
        }
    }

    private func soundFor(_ event: BarEvent) -> SoundRole? {
        let sounds = config.sounds
        if event.layer == "poly" {
            var poly = sounds.poly
            if event.role == "polyAccent" {
                poly.vol = min(1.0, poly.accentVol ?? 1.0)
            }
            return poly
        }
        switch event.role {
        case "downbeat": return sounds.downbeat
        case "subdivision": return sounds.subdivision
        default: return sounds.beat
        }
    }
}
