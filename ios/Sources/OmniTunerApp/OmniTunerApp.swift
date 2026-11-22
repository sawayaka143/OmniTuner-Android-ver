import SwiftUI

public enum RootTab: Hashable {
    case tuner
    case metronome
    case chords
    case scales
}

@main
struct OmniTunerApp: App {
    @State private var container: AppContainer
    @State private var tunerModel: TunerModel
    @State private var metronomeModel: MetronomeModelView
    @State private var chordsModel: ChordFinderModel
    @State private var scalesModel: ScalesModelView

    init() {
        let appContainer = AppContainer()
        _container = State(initialValue: appContainer)
        _tunerModel = State(initialValue: TunerModel(container: appContainer))
        _metronomeModel = State(initialValue: MetronomeModelView(container: appContainer))
        _chordsModel = State(initialValue: ChordFinderModel(container: appContainer))
        _scalesModel = State(initialValue: ScalesModelView(container: appContainer))
    }

    var body: some Scene {
        WindowGroup {
            RootShellView(
                container: container,
                tunerModel: tunerModel,
                metronomeModel: metronomeModel,
                chordsModel: chordsModel,
                scalesModel: scalesModel
            )
            .preferredColorScheme(container.appearance.value.themeMode.colorScheme)
        }
    }
}

private struct RootShellView: View {
    @Environment(\.colorScheme) private var systemColorScheme

    @Bindable var container: AppContainer
    @Bindable var tunerModel: TunerModel
    @Bindable var metronomeModel: MetronomeModelView
    @Bindable var chordsModel: ChordFinderModel
    @Bindable var scalesModel: ScalesModelView

    @State private var selectedTab: RootTab = .tuner
    @State private var settingsOpen = false

    private var isDark: Bool {
        switch container.appearance.value.themeMode {
        case .system: return systemColorScheme == .dark
        case .light: return false
        case .dark: return true
        }
    }

    var body: some View {
        let palette = OmniPalette(accentPreset: container.appearance.value.accent, isDark: isDark)

        TabView(selection: $selectedTab) {
            Tab("Tuner", systemImage: "tuningfork", value: .tuner) {
                TunerScreen(
                    model: tunerModel,
                    isActiveTab: selectedTab == .tuner && !settingsOpen,
                    onOpenSettings: { settingsOpen = true }
                )
            }

            Tab("Metronome", systemImage: "metronome.fill", value: .metronome) {
                MetronomeScreen(
                    model: metronomeModel,
                    onOpenSettings: { settingsOpen = true }
                )
            }

            Tab("Chords", systemImage: "guitars.fill", value: .chords) {
                ChordFinderScreen(
                    model: chordsModel,
                    onOpenSettings: { settingsOpen = true }
                )
            }

            Tab("Scales", systemImage: "music.note.list", value: .scales) {
                ScalesScreen(
                    model: scalesModel,
                    onOpenSettings: { settingsOpen = true }
                )
            }
        }
        .symbolEffect(.bounce, value: selectedTab)
        .tint(palette.accent)
        .environment(\.omniPalette, palette)
        .sheet(isPresented: $settingsOpen) {
            SettingsScreen(container: container)
                .environment(\.omniPalette, palette)
                .preferredColorScheme(container.appearance.value.themeMode.colorScheme)
        }
    }
}
