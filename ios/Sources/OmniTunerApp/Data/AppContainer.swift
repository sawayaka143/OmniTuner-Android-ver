import Foundation
import OmniTunerCore

@MainActor
@Observable
public final class AppContainer {
    public let appearance: PersistedStore<AppearanceSettings>
    public let tuner: PersistedStore<TunerSettings>
    public let scales: PersistedStore<ScaleSettings>
    public let chords: PersistedStore<ChordSettings>
    public let metronome: PersistedStore<MetronomeState>
    public let metronomePresets: PersistedStore<[MetronomePreset]>
    public let instruments: InstrumentLibrary
    public let noteAudio: NoteAudio

    public init(defaults: UserDefaults = .standard) {
        self.appearance = PersistedStore(key: "appearance", defaultValue: AppearanceSettings(), defaults: defaults)
        self.tuner = PersistedStore(key: "tuner.settings", defaultValue: TunerSettings(), defaults: defaults)
        self.scales = PersistedStore(key: "scales.settings", defaultValue: ScaleSettings(), defaults: defaults)
        self.chords = PersistedStore(key: "chords.settings", defaultValue: ChordSettings(), defaults: defaults)
        self.metronome = PersistedStore(key: "metronome.state", defaultValue: defaultMetronomeState, defaults: defaults)
        self.metronomePresets = PersistedStore(key: "metronome.presets", defaultValue: [], defaults: defaults)
        self.instruments = InstrumentLibrary(defaults: defaults)
        self.noteAudio = NoteAudio()
    }
}
