import Foundation

public struct ParsedWav: Sendable {
    public let sampleRate: Int
    public let channels: Int
    public let samples: [Float]

    public init(sampleRate: Int, channels: Int, samples: [Float]) {
        self.sampleRate = sampleRate
        self.channels = channels
        self.samples = samples
    }
}

public enum WavParseError: Error, LocalizedError, Sendable {
    case invalidHeader(String)

    public var errorDescription: String? {
        switch self {
        case .invalidHeader(let msg): return msg
        }
    }
}

public enum WavParser {
    public static func parse(_ data: Data) throws -> ParsedWav {
        let bytes = [UInt8](data)
        guard bytes.count >= 44 else { throw WavParseError.invalidHeader("WAV file too small") }
        guard bytes[0] == UInt8(ascii: "R"), bytes[1] == UInt8(ascii: "I") else {
            throw WavParseError.invalidHeader("not a RIFF file")
        }
        guard bytes[8] == UInt8(ascii: "W"), bytes[9] == UInt8(ascii: "A") else {
            throw WavParseError.invalidHeader("not a WAVE file")
        }

        var offset = 12
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLength = -1

        while offset + 8 <= bytes.count {
            let chunkId = String(bytes: bytes[offset..<(offset + 4)], encoding: .ascii) ?? ""
            let chunkSize = readInt32LE(bytes, offset: offset + 4)
            let bodyOffset = offset + 8

            switch chunkId {
            case "fmt ":
                guard bodyOffset + 16 <= bytes.count else {
                    throw WavParseError.invalidHeader("truncated fmt chunk")
                }
                format = readInt16LE(bytes, offset: bodyOffset)
                channels = readInt16LE(bytes, offset: bodyOffset + 2)
                sampleRate = readInt32LE(bytes, offset: bodyOffset + 4)
                bitsPerSample = readInt16LE(bytes, offset: bodyOffset + 14)
            case "data":
                dataOffset = bodyOffset
                dataLength = min(chunkSize, bytes.count - bodyOffset)
            default:
                break
            }

            offset = bodyOffset + chunkSize + (chunkSize % 2 == 1 ? 1 : 0)
        }

        guard format == 1 else {
            throw WavParseError.invalidHeader("only PCM WAV supported, got format \(format)")
        }
        guard dataOffset >= 0, dataLength > 0 else {
            throw WavParseError.invalidHeader("no data chunk")
        }
        guard bitsPerSample == 24 || bitsPerSample == 16 else {
            throw WavParseError.invalidHeader("unsupported bit depth \(bitsPerSample)")
        }
        guard channels > 0 else {
            throw WavParseError.invalidHeader("invalid channel count")
        }

        let bytesPerSample = bitsPerSample / 8
        let frameCount = dataLength / (bytesPerSample * channels)
        var samples = [Float](repeating: 0.0, count: frameCount * channels)

        var pos = dataOffset
        var outIndex = 0
        for _ in 0..<frameCount {
            for _ in 0..<channels {
                if bitsPerSample == 16 {
                    let raw = readInt16LE(bytes, offset: pos)
                    let signed = raw >= 0x8000 ? raw - 0x10000 : raw
                    samples[outIndex] = Float(signed) / 32768.0
                } else {
                    let value = Int(bytes[pos]) | (Int(bytes[pos + 1]) << 8) | (Int(bytes[pos + 2]) << 16)
                    let signed = value >= 0x800000 ? value - 0x1000000 : value
                    samples[outIndex] = Float(signed) / 8388608.0
                }
                outIndex += 1
                pos += bytesPerSample
            }
        }

        return ParsedWav(sampleRate: sampleRate, channels: channels, samples: samples)
    }

    private static func readInt16LE(_ bytes: [UInt8], offset: Int) -> Int {
        Int(bytes[offset]) | (Int(bytes[offset + 1]) << 8)
    }

    private static func readInt32LE(_ bytes: [UInt8], offset: Int) -> Int {
        Int(bytes[offset]) |
            (Int(bytes[offset + 1]) << 8) |
            (Int(bytes[offset + 2]) << 16) |
            (Int(bytes[offset + 3]) << 24)
    }
}
