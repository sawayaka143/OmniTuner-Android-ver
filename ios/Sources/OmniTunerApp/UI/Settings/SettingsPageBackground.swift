import SwiftUI

/// Settings pages sit on the palette's grouped page color (pure black in the dark theme).
/// Light theme keeps the stock grouped-list background, so only the dark case is overridden.
private struct SettingsPageBackground: ViewModifier {
    @Environment(\.omniPalette) private var palette

    func body(content: Content) -> some View {
        if palette.isDark {
            content
                .scrollContentBackground(.hidden)
                .background(palette.groupedPage.ignoresSafeArea())
        } else {
            content
        }
    }
}

extension View {
    func settingsPageBackground() -> some View {
        modifier(SettingsPageBackground())
    }
}
