import SwiftUI

/// Settings pages wear Apple's grouped-list colors at the base interface level (what the Settings app
/// uses). Presented in a sheet, iOS would otherwise lift the page and cards to the lighter "elevated" greys.
private struct SettingsPageBackground: ViewModifier {
    @Environment(\.omniPalette) private var palette

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(palette.groupedPage.ignoresSafeArea())
    }
}

private struct SettingsRowBackground: ViewModifier {
    @Environment(\.omniPalette) private var palette

    func body(content: Content) -> some View {
        content.listRowBackground(palette.groupedCard)
    }
}

extension View {
    func settingsPageBackground() -> some View {
        modifier(SettingsPageBackground())
    }

    /// Put on a section's content (not on the `Form`) so its rows get the card color.
    func settingsRows() -> some View {
        modifier(SettingsRowBackground())
    }
}
