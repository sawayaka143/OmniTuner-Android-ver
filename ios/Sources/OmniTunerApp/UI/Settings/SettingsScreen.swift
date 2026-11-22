import SwiftUI
import UIKit

public struct SettingsScreen: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @Bindable var container: AppContainer

    private static let privacyPolicyURL = URL(string: "https://omni-tuner.vercel.app/privacy")!

    public init(container: AppContainer) {
        self.container = container
    }

    /// The language the app is running in, written in that language (like iOS Settings does).
    private var currentLanguageName: String {
        let id = Bundle.main.preferredLocalizations.first ?? "en"
        return Locale(identifier: id).localizedString(forIdentifier: id)?.localizedCapitalized ?? id
    }

    /// A filled dot bitmap in the preset's color as it looks in the current theme.
    /// Rendered via `UIGraphicsImageRenderer` rather than tinting an SF Symbol so `UIMenu`
    /// never falls back to a monochrome black glyph.
    private func dot(for preset: AccentColorPreset) -> UIImage {
        let color = OmniPalette(accentPreset: preset, isDark: palette.isDark).accent
        let size = CGSize(width: 14, height: 14)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            UIColor(color).setFill()
            ctx.cgContext.fillEllipse(in: CGRect(origin: .zero, size: size))
        }.withRenderingMode(.alwaysOriginal)
    }

    public var body: some View {
        NavigationStack {
            Form {
              Group {
                Section {
                    Picker(selection: Binding(
                        get: { container.appearance.value.themeMode },
                        set: { next in container.appearance.update { $0.themeMode = next } }
                    )) {
                        ForEach(ThemeMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    } label: {
                        Text("Theme", bundle: .module)
                    }
                    .pickerStyle(.menu)

                    // A pull-down like the other pickers, with each color's dot beside its name
                    // (menus tint template images, so the dots are original-rendered bitmaps).
                    LabeledContent {
                        Menu {
                            Picker(selection: Binding(
                                get: { container.appearance.value.accent },
                                set: { next in container.appearance.update { $0.accent = next } }
                            )) {
                                ForEach(AccentColorPreset.allCases) { preset in
                                    Label {
                                        Text(preset.label)
                                    } icon: {
                                        Image(uiImage: dot(for: preset))
                                    }
                                    .tag(preset)
                                }
                            } label: {
                                Text("Accent color", bundle: .module)
                            }
                            .pickerStyle(.inline)
                        } label: {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(palette.accent)
                                    .frame(width: 12, height: 12)
                                Text(container.appearance.value.accent.label)
                                Image(systemName: "chevron.up.chevron.down")
                                    .font(.footnote.weight(.semibold))
                            }
                            .foregroundStyle(Color.secondary)
                        }
                    } label: {
                        Text("Accent color", bundle: .module)
                    }

                    // Per-app language lives in iOS Settings -> OmniTuner -> Language.
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    } label: {
                        HStack {
                            // Color.*, not the hierarchical .primary: inside a Button that resolves to the tint.
                            Text("Language", bundle: .module)
                                .foregroundStyle(Color.primary)
                            Spacer()
                            Text(currentLanguageName)
                                .foregroundStyle(Color.secondary)
                            Image(systemName: "arrow.up.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.secondary)
                        }
                    }
                }

                Section {
                    Picker(selection: Binding(
                        get: { container.tuner.value.startupMode },
                        set: { next in container.tuner.update { $0.startupMode = next } }
                    )) {
                        ForEach(StartupMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    } label: {
                        Text("Open tuner in", bundle: .module)
                    }
                    .pickerStyle(.menu)

                    NavigationLink {
                        ReferencePitchView(container: container)
                    } label: {
                        LabeledContent {
                            Text("\(container.tuner.value.referencePitch) Hz", bundle: .module)
                        } label: {
                            Text("Reference pitch", bundle: .module)
                        }
                    }

                    Toggle(isOn: Binding(
                        get: { container.tuner.value.proAccuracy },
                        set: { next in container.tuner.update { $0.proAccuracy = next } }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Pro accuracy", bundle: .module)
                            Text("Show the exact offset in cents", bundle: .module)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section {
                    Toggle(isOn: Binding(
                        get: { container.tuner.value.inTuneFeedback },
                        set: { next in container.tuner.update { $0.inTuneFeedback = next } }
                    )) {
                        Text("Show in-tune feedback", bundle: .module)
                    }

                    Toggle(isOn: Binding(
                        get: { container.tuner.value.chime },
                        set: { next in container.tuner.update { $0.chime = next } }
                    )) {
                        Text("Play chime", bundle: .module)
                    }

                    Toggle(isOn: Binding(
                        get: { container.tuner.value.haptics },
                        set: { next in container.tuner.update { $0.haptics = next } }
                    )) {
                        Text("Vibrate", bundle: .module)
                    }
                }

                Section {
                    LabeledContent {
                        Text("1.0 (iOS)")
                    } label: {
                        Text("Version", bundle: .module)
                    }

                    Link(destination: Self.privacyPolicyURL) {
                        HStack {
                            Text("Privacy policy", bundle: .module)
                                .foregroundStyle(Color.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.secondary)
                        }
                    }

                    NavigationLink {
                        LicensesView()
                    } label: {
                        Text("Open-source licenses", bundle: .module)
                    }
                }
              }
              .settingsRows()
            }
            .settingsPageBackground()
            .navigationTitle(Text("Settings", bundle: .module))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .close) {
                        dismiss()
                    }
                }
            }
        }
        .tint(palette.accent)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}
