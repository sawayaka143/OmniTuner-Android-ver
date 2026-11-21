import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public enum ThemeMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case system, light, dark
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
    public var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

public enum AccentColorPreset: String, CaseIterable, Codable, Sendable, Identifiable {
    case blue, cyan, green, lime, yellow, orange, pink, black
    public var id: String { rawValue }
    public var label: String {
        rawValue.prefix(1).uppercased() + rawValue.dropFirst()
    }
}

public struct OmniPalette: Sendable {
    public let isDark: Bool
    public let accent: Color
    public let onAccent: Color

    // Pedalboard surfaces (DESIGN.md: pure white / AMOLED black + single board grey per theme)
    public let paper: Color
    public let boardGrey: Color
    public let groupedPage: Color
    public let groupedCard: Color
    public let ink: Color
    public let controlInk: Color
    public let onControlInk: Color
    public let mutedLabel: Color
    public let hairline: Color

    // Semantic & Chords status colors
    public let goodGreen: Color
    public let offPitchRed: Color
    public let warnAmber: Color
    public let infoViolet: Color

    public init(accentPreset: AccentColorPreset, isDark: Bool) {
        self.isDark = isDark
        let accentHex: UInt32
        switch accentPreset {
        case .blue: accentHex = 0x2B67FE
        case .cyan: accentHex = 0x00B2FF
        case .green: accentHex = 0x0EB4A0
        case .lime: accentHex = 0xB2D20C
        case .yellow: accentHex = 0xFFCC32
        case .orange: accentHex = 0xFF7F65
        case .pink: accentHex = 0xFC48B3
        case .black: accentHex = isDark ? 0xF2F2F2 : 0x0E0E0E
        }
        let accentColor = Color(hex: accentHex)
        self.accent = accentColor
        self.onAccent = Self.relativeLuminance(hex: accentHex) > 0.179 ? .black : .white

        if isDark {
            self.paper = Color(hex: 0x000000)
            self.boardGrey = Color(hex: 0x1C1C1C)
            self.groupedPage = Color(hex: 0x000000)
            self.groupedCard = Color(hex: 0x1C1C1C)
            self.ink = Color(hex: 0xFFFFFF)
            self.controlInk = Color(hex: 0xECECEC)
            self.onControlInk = Color(hex: 0x121212)
            self.mutedLabel = Color(hex: 0x868688)
            self.hairline = Color(hex: 0x333333)
            self.goodGreen = Color(hex: 0x8FD3A8)
            self.offPitchRed = Color(hex: 0xF2B8B5)
            self.warnAmber = Color(hex: 0xC07A5E)
            self.infoViolet = Color(hex: 0x9A8FB8)
        } else {
            self.paper = Color(hex: 0xFFFFFF)
            self.boardGrey = Color(hex: 0xF3F3F2)
            self.groupedPage = Color(hex: 0xF3F3F2)
            self.groupedCard = Color(hex: 0xFFFFFF)
            self.ink = Color(hex: 0x000000)
            self.controlInk = Color(hex: 0x171717)
            self.onControlInk = Color(hex: 0xFFFFFF)
            self.mutedLabel = Color(hex: 0x868688)
            self.hairline = Color(hex: 0xDADADA)
            self.goodGreen = Color(hex: 0x1E7A45)
            self.offPitchRed = Color(hex: 0xB3261E)
            self.warnAmber = Color(hex: 0x8A4D00)
            self.infoViolet = Color(hex: 0x544A8F)
        }
    }

    private static func relativeLuminance(hex: UInt32) -> Double {
        func linear(_ c: Double) -> Double {
            c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let r = linear(Double((hex >> 16) & 0xFF) / 255.0)
        let g = linear(Double((hex >> 8) & 0xFF) / 255.0)
        let b = linear(Double(hex & 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    func interpolated(to other: Color, fraction: Double) -> Color {
        let t = min(1.0, max(0.0, fraction))
        #if canImport(UIKit)
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        UIColor(self).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return Color(
            .sRGB,
            red: Double(r1 + (r2 - r1) * t),
            green: Double(g1 + (g2 - g1) * t),
            blue: Double(b1 + (b2 - b1) * t),
            opacity: Double(a1 + (a2 - a1) * t)
        )
        #else
        return t < 0.5 ? self : other
        #endif
    }
}

private struct OmniPaletteKey: EnvironmentKey {
    static let defaultValue = OmniPalette(accentPreset: .pink, isDark: false)
}

public extension EnvironmentValues {
    var omniPalette: OmniPalette {
        get { self[OmniPaletteKey.self] }
        set { self[OmniPaletteKey.self] = newValue }
    }
}
