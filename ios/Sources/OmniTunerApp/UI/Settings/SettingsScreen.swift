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

    public var body: some View {
        NavigationStack {
            Form {
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

                    Picker(selection: Binding(
                        get: { container.appearance.value.accent },
                        set: { next in container.appearance.update { $0.accent = next } }
                    )) {
                        ForEach(AccentColorPreset.allCases) { preset in
                            Text(preset.label).tag(preset)
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Text("Accent color", bundle: .module)
                            Circle()
                                .fill(palette.accent)
                                .frame(width: 10, height: 10)
                        }
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
