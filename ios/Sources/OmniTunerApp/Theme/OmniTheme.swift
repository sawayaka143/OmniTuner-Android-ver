import SwiftUI
import UIKit

public enum ThemeMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case system, light, dark
    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .system: String(localized: "System", bundle: .module)
        case .light: String(localized: "Light", bundle: .module)
        case .dark: String(localized: "Dark", bundle: .module)
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
        switch self {
        case .blue: String(localized: "Blue", bundle: .module)
        case .cyan: String(localized: "Cyan", bundle: .module)
        case .green: String(localized: "Green", bundle: .module)
        case .lime: String(localized: "Lime", bundle: .module)
        case .yellow: String(localized: "Yellow", bundle: .module)
        case .orange: String(localized: "Orange", bundle: .module)
        case .pink: String(localized: "Pink", bundle: .module)
        case .black: String(localized: "Black", bundle: .module)
        }
    }

    /// Apple's system color for the preset ("Lime" is `systemMint`, the nearest system color;
    /// "Pink" uses OmniTuner's `#FC48B3` because `systemPink` reads as red;
    /// "Black" is the primary label color: black in light, white in dark).
    fileprivate var systemColor: UIColor {
        switch self {
        case .blue: .systemBlue
        case .cyan: .systemCyan
        case .green: .systemGreen
        case .lime: .systemMint
        case .yellow: .systemYellow
        case .orange: .systemOrange
        case .pink: UIColor(red: 0xFC / 255.0, green: 0x48 / 255.0, blue: 0xB3 / 255.0, alpha: 1.0)
        case .black: .label
        }
    }
}

/// Every color is one of Apple's system colors, resolved for the app's current light/dark theme at
/// the base interface level (the level Apple's own Settings uses). Resolving at `.base` keeps grouped
/// pages and cards from picking up the lighter "elevated" greys when shown inside a sheet.
public struct OmniPalette: Sendable {
    public let isDark: Bool
    public let accent: Color
    public let onAccent: Color

    public let paper: Color
    public let boardGrey: Color
    public let groupedPage: Color
    public let groupedCard: Color
    public let ink: Color
    public let mutedLabel: Color
    public let hairline: Color

    // Semantic & Chords status colors
    public let goodGreen: Color
    public let offPitchRed: Color
    public let warnAmber: Color
    public let infoViolet: Color

    public init(accentPreset: AccentColorPreset, isDark: Bool) {
        self.isDark = isDark
        // `init(traitsFrom:)` is deprecated, but its replacement mutates main-actor-isolated
        // properties, which this nonisolated init can't do under Swift 6.
        let traits = UITraitCollection(traitsFrom: [
            UITraitCollection(userInterfaceStyle: isDark ? .dark : .light),
            UITraitCollection(userInterfaceLevel: .base),
        ])
        func resolve(_ color: UIColor) -> UIColor { color.resolvedColor(with: traits) }

        let accentUI = resolve(accentPreset.systemColor)
        self.accent = Color(uiColor: accentUI)
        self.onAccent = Self.relativeLuminance(of: accentUI) > 0.179 ? .black : .white

        self.paper = Color(uiColor: resolve(.systemBackground))
        self.boardGrey = Color(uiColor: resolve(.secondarySystemBackground))
        self.groupedPage = isDark ? Color(hex: 0x000000) : Color(hex: 0xF2F2F6)
        self.groupedCard = isDark ? Color(hex: 0x1C1C1E) : Color(hex: 0xFFFFFE)
        self.ink = Color(uiColor: resolve(.label))
        self.mutedLabel = Color(uiColor: resolve(.secondaryLabel))
        // Opaque variant: the hairline is also used as a fill (fret dots), where the translucent one would fade.
        self.hairline = Color(uiColor: resolve(.opaqueSeparator))

        self.goodGreen = Color(uiColor: resolve(.systemGreen))
        self.offPitchRed = Color(uiColor: resolve(.systemRed))
        self.warnAmber = Color(uiColor: resolve(.systemOrange))
        self.infoViolet = Color(uiColor: resolve(.systemIndigo))
    }

    private static func relativeLuminance(of color: UIColor) -> Double {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        func linear(_ c: CGFloat) -> Double {
            let c = Double(c)
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}

public extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }

    func interpolated(to other: Color, fraction: Double) -> Color {
        let t = min(1.0, max(0.0, fraction))
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
