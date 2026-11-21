import Foundation
import OmniTunerCore

public struct InstrumentSelection: Codable, Hashable, Sendable {
    public var instrumentId: String = "guitar"
    public var tuningId: String = "standard"
}

public struct InstrumentTunings: Identifiable, Hashable, Sendable {
    public var id: String { instrument.id }
    public let instrument: Instrument
    public let tunings: [Tuning]
}

public struct RecentTuning: Identifiable, Hashable, Sendable {
    public var id: String { "\(instrument.id)|\(tuning.id)" }
    public let instrument: Instrument
    public let tuning: Tuning
}

public struct LibraryState: Sendable {
    public let instruments: [Instrument]
    public let instrument: Instrument
    public let tunings: [Tuning]
    public let tuning: Tuning
    public let catalog: [InstrumentTunings]
    public let recent: [RecentTuning]
}

public let maxRecentTunings = 5

@MainActor
@Observable
public final class InstrumentLibrary {
    private let customInstrumentsStore: PersistedStore<[CustomInstrument]>
    private let customTuningsStore: PersistedStore<[CustomTuning]>
    private let selectionStore: PersistedStore<InstrumentSelection>
    private let recentStore: PersistedStore<[InstrumentSelection]>

    public private(set) var state: LibraryState

    public init(defaults: UserDefaults = .standard) {
        let ci = PersistedStore<[CustomInstrument]>(key: "instruments.custom", defaultValue: [], defaults: defaults)
        let ct = PersistedStore<[CustomTuning]>(key: "tunings.custom", defaultValue: [], defaults: defaults)
        let sel = PersistedStore<InstrumentSelection>(key: "instruments.selection", defaultValue: InstrumentSelection(), defaults: defaults)
        let rec = PersistedStore<[InstrumentSelection]>(key: "tunings.recent", defaultValue: [], defaults: defaults)
        self.customInstrumentsStore = ci
        self.customTuningsStore = ct
        self.selectionStore = sel
        self.recentStore = rec
        self.state = Self.resolve(
            instruments: ci.value,
            tunings: ct.value,
            current: sel.value,
            recentSelections: rec.value
        )
    }

    public func selectInstrument(_ instrumentId: String) {
        guard let instrument = state.instruments.first(where: { $0.id == instrumentId }) else { return }
        let tunings = instrument.tunings +
            customTuningsStore.value.filter { $0.instrumentId == instrumentId }.map { $0.toTuning() }
        guard let firstTuning = tunings.first else { return }
        choose(InstrumentSelection(instrumentId: instrument.id, tuningId: firstTuning.id))
    }

    public func selectTuning(_ tuningId: String) {
        choose(InstrumentSelection(instrumentId: selectionStore.value.instrumentId, tuningId: tuningId))
    }

    public func select(instrumentId: String, tuningId: String) {
        choose(InstrumentSelection(instrumentId: instrumentId, tuningId: tuningId))
    }

    public func saveInstrument(editingId: String?, name: String, stringCount: Int, notes: [Int]) {
        let id = editingId ?? "instr-\(UUID().uuidString)"
        let record = CustomInstrument(id: id, name: name, stringCount: stringCount, defaultNotes: notes)
        customInstrumentsStore.update { list in
            if let idx = list.firstIndex(where: { $0.id == id }) {
                list[idx] = record
            } else {
                list.append(record)
            }
        }
        choose(InstrumentSelection(instrumentId: id, tuningId: "\(id)-default"))
    }

    public func deleteInstrument(_ id: String) {
        customInstrumentsStore.update { $0.removeAll { $0.id == id } }
        customTuningsStore.update { $0.removeAll { $0.instrumentId == id } }
        refresh()
    }

    public func saveTuning(editingId: String?, instrumentId: String, name: String, notes: [Int]) {
        let id = editingId ?? "custom-\(UUID().uuidString)"
        let record = CustomTuning(id: id, instrumentId: instrumentId, name: name, notes: notes)
        customTuningsStore.update { list in
            if let idx = list.firstIndex(where: { $0.id == id }) {
                list[idx] = record
            } else {
                list.append(record)
            }
        }
        choose(InstrumentSelection(instrumentId: instrumentId, tuningId: id))
    }

    public func deleteTuning(_ id: String) {
        customTuningsStore.update { $0.removeAll { $0.id == id } }
        refresh()
    }

    private func choose(_ next: InstrumentSelection) {
        let previous = selectionStore.value
        selectionStore.value = next
        recentStore.update { list in
            var deduped: [InstrumentSelection] = []
            for item in [next, previous] + list where !deduped.contains(item) {
                deduped.append(item)
            }
            list = Array(deduped.prefix(maxRecentTunings + 1))
        }
        refresh()
    }

    private func refresh() {
        state = Self.resolve(
            instruments: customInstrumentsStore.value,
            tunings: customTuningsStore.value,
            current: selectionStore.value,
            recentSelections: recentStore.value
        )
    }

    private static func resolve(
        instruments: [CustomInstrument],
        tunings: [CustomTuning],
        current: InstrumentSelection,
        recentSelections: [InstrumentSelection]
    ) -> LibraryState {
        let all = builtInInstruments + instruments.map { $0.toInstrument() }
        let catalog = all.map { entry in
            InstrumentTunings(
                instrument: entry,
                tunings: entry.tunings + tunings.filter { $0.instrumentId == entry.id }.map { $0.toTuning() }
            )
        }
        let instrument = all.first(where: { $0.id == current.instrumentId }) ?? builtInInstruments[0]
        let available = catalog.first(where: { $0.instrument.id == instrument.id })?.tunings ?? instrument.tunings
        let tuning = available.first(where: { $0.id == current.tuningId }) ?? available[0]

        var combinedRecents: [InstrumentSelection] = []
        for item in [InstrumentSelection(instrumentId: instrument.id, tuningId: tuning.id)] + recentSelections {
            if !combinedRecents.contains(item) { combinedRecents.append(item) }
        }
        let recent: [RecentTuning] = combinedRecents.compactMap { pick in
            guard let entry = catalog.first(where: { $0.instrument.id == pick.instrumentId }),
                  let found = entry.tunings.first(where: { $0.id == pick.tuningId }) else { return nil }
            return RecentTuning(instrument: entry.instrument, tuning: found)
        }
        return LibraryState(
            instruments: all,
            instrument: instrument,
            tunings: available,
            tuning: tuning,
            catalog: catalog,
            recent: Array(recent.prefix(maxRecentTunings))
        )
    }
}

private extension CustomInstrument {
    func toInstrument() -> Instrument {
        Instrument(
            id: id,
            label: name,
            stringCount: stringCount,
            tunings: [
                Tuning(
                    id: "\(id)-default",
                    label: "Default",
                    strings: defaultNotes.map { $0.toNamedFrequency() },
                    isCustom: true
                )
            ],
            isCustom: true
        )
    }
}

private extension CustomTuning {
    func toTuning() -> Tuning {
        Tuning(
            id: id,
            label: name,
            strings: notes.map { $0.toNamedFrequency() },
            isCustom: true
        )
    }
}

private extension Int {
    func toNamedFrequency() -> NamedFrequency {
        NamedFrequency(name: midiNoteLabel(self), freq: midiNoteToFrequency(self))
    }
}
