public struct NamedFrequency: Sendable, Hashable, Codable {
    public let name: String
    public let freq: Double

    public init(name: String, freq: Double) {
        self.name = name
        self.freq = freq
    }
}
