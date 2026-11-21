import SwiftUI

public struct SettingsScreen: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    @Bindable var container: AppContainer

    private static let privacyPolicyURL = URL(string: "https://omni-tuner.vercel.app/privacy")!

    public init(container: AppContainer) {
        self.container = container
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Theme", selection: Binding(
                        get: { container.appearance.value.themeMode },
                        set: { next in container.appearance.update { $0.themeMode = next } }
                    )) {
                        ForEach(ThemeMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
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
                            Text("Accent color")
                            Circle()
                                .fill(palette.accent)
                                .frame(width: 10, height: 10)
                        }
                    }
                }

                Section("Tuner") {
                    Picker("Open tuner in", selection: Binding(
                        get: { container.tuner.value.startupMode },
                        set: { next in container.tuner.update { $0.startupMode = next } }
                    )) {
                        ForEach(StartupMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }

                    NavigationLink {
                        ReferencePitchView(container: container)
                    } label: {
                        LabeledContent("Reference pitch", value: "\(container.tuner.value.referencePitch) Hz")
                    }

                    Toggle(isOn: Binding(
                        get: { container.tuner.value.proAccuracy },
                        set: { next in container.tuner.update { $0.proAccuracy = next } }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Pro accuracy")
                            Text("Show the exact offset in cents")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section("In-tune feedback") {
                    Toggle("Show in-tune feedback", isOn: Binding(
                        get: { container.tuner.value.inTuneFeedback },
                        set: { next in container.tuner.update { $0.inTuneFeedback = next } }
                    ))

                    Toggle("Play chime", isOn: Binding(
                        get: { container.tuner.value.chime },
                        set: { next in container.tuner.update { $0.chime = next } }
                    ))

                    Toggle("Vibrate", isOn: Binding(
                        get: { container.tuner.value.haptics },
                        set: { next in container.tuner.update { $0.haptics = next } }
                    ))
                }

                Section("About") {
                    LabeledContent("Version", value: "1.0 (iOS)")

                    Link(destination: Self.privacyPolicyURL) {
                        HStack {
                            Text("Privacy policy")
                                .foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                    }

                    NavigationLink("Open-source licenses") {
                        LicensesView()
                    }
                }
            }
            .navigationTitle("Settings")
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
