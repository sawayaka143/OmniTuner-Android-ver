import Foundation

public struct GuitarSample: Sendable, Hashable {
    public let file: String
    public let rootMidi: Int

    public init(file: String, rootMidi: Int) {
        self.file = file
        self.rootMidi = rootMidi
    }
}

public let guitarSampleMinMidi = 37
public let guitarSampleMaxMidi = 86

public let guitarSamples: [GuitarSample] = [
    GuitarSample(file: "db2_mf_rr1.wav", rootMidi: 37),
    GuitarSample(file: "e2_mf_rr1.wav", rootMidi: 40),
    GuitarSample(file: "gb2_mf_rr1.wav", rootMidi: 42),
    GuitarSample(file: "a2_mf_rr1.wav", rootMidi: 45),
    GuitarSample(file: "c3_mf_rr1.wav", rootMidi: 48),
    GuitarSample(file: "eb3_mf_rr1.wav", rootMidi: 51),
    GuitarSample(file: "gb3_mf_rr1.wav", rootMidi: 54),
    GuitarSample(file: "a3_mf_rr1.wav", rootMidi: 57),
    GuitarSample(file: "c4_mf_rr1.wav", rootMidi: 60),
    GuitarSample(file: "eb4_mf_rr1.wav", rootMidi: 63),
    GuitarSample(file: "gb4_mf_rr1.wav", rootMidi: 66),
    GuitarSample(file: "a4_mf_rr1.wav", rootMidi: 69),
    GuitarSample(file: "c5_mf_rr1.wav", rootMidi: 72),
    GuitarSample(file: "eb5_mf_rr1.wav", rootMidi: 75),
    GuitarSample(file: "gb5_mf_rr1.wav", rootMidi: 78),
    GuitarSample(file: "a5_mf_rr1.wav", rootMidi: 81),
    GuitarSample(file: "c6_mf_rr1.wav", rootMidi: 84),
    GuitarSample(file: "d6_mf_rr1.wav", rootMidi: 86),
]

public func nearestGuitarSample(midi: Int) -> GuitarSample? {
    if midi < guitarSampleMinMidi || midi > guitarSampleMaxMidi { return nil }
    var best = guitarSamples[0]
    for sample in guitarSamples {
        if abs(sample.rootMidi - midi) < abs(best.rootMidi - midi) {
            best = sample
        }
    }
    return best
}

public func guitarSamplePlaybackRate(midi: Int, rootMidi: Int) -> Double {
    pow(2.0, Double(midi - rootMidi) / 12.0)
}
