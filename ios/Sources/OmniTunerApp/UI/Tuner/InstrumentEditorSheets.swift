import OmniTunerCore
import SwiftUI

private let guitarStandardNotes = [40, 45, 50, 55, 59, 64]

public struct TuningDraft: Identifiable {
    public let id = UUID()
    public let editingId: String?
    public let name: String
    public let notes: [Int]
    public let baseId: String?

    public init(editingId: String?, name: String, notes: [Int], baseId: String?) {
        self.editingId = editingId
        self.name = name
        self.notes = notes
        self.baseId = baseId
    }
}

public struct InstrumentDraft: Identifiable {
    public let id = UUID()
    public let editingId: String?
    public let name: String
    public let stringCount: Int
    public let notes: [Int]
    public let baseId: String?

    public init(editingId: String?, name: String, stringCount: Int, notes: [Int], baseId: String?) {
        self.editingId = editingId
        self.name = name
        self.stringCount = stringCount
        self.notes = notes
        self.baseId = baseId
    }
}

public extension Tuning {
    func midiNotes() -> [Int] {
        strings.map { frequencyToMidiNote($0.freq) ?? 69 }
    }
}

public extension Instrument {
    func defaultNotes() -> [Int] {
        tunings.first?.midiNotes() ?? [Int](repeating: 40, count: stringCount)
    }
}

public struct TuningEditorSheet: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let draft: TuningDraft
    public let instrument: Instrument
    public let presets: [Tuning]
    public let onSave: (String, [Int]) -> String?

    @State private var name: String
    @State private var notes: [Int]
    @State private var baseId: String?
    @State private var error: String? = nil

    public init(draft: TuningDraft, instrument: Instrument, presets: [Tuning], onSave: @escaping (String, [Int]) -> String?) {
        self.draft = draft
        self.instrument = instrument
        self.presets = presets
        self.onSave = onSave
        self._name = State(initialValue: draft.name)
        self._notes = State(initialValue: draft.notes)
        self._baseId = State(initialValue: draft.baseId)
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Tuning name", text: $name)
                        .textInputAutocapitalization(.words)
                        .onChange(of: name) { _, newValue in
                            if newValue.count > maxCustomTuningNameLength {
                                name = String(newValue.prefix(maxCustomTuningNameLength))
                            }
                            error = nil
                        }
                } header: {
                    Text("Name")
                } footer: {
                    if let error {
                        Text(error)
                            .foregroundStyle(palette.offPitchRed)
                    }
                }

                if !presets.isEmpty {
                    Section("Start from") {
                        Picker("Preset", selection: $baseId) {
                            ForEach(presets) { preset in
                                Text(preset.label).tag(Optional(preset.id))
                            }
                        }
                        .onChange(of: baseId) { _, next in
                            if let preset = presets.first(where: { $0.id == next }) {
                                notes = preset.midiNotes()
                            }
                        }
                    }
                }

                let baseNotes = presets.first(where: { $0.id == baseId })?.midiNotes()
                Section(instrument.label) {
                    StringNotesEditor(notes: $notes, baseNotes: baseNotes)
                }
            }
            .navigationTitle(draft.editingId == nil ? "New tuning" : "Edit tuning")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(role: .cancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .confirm) {
                        if let err = onSave(name.trimmingCharacters(in: .whitespacesAndNewlines), notes) {
                            error = err
                        } else {
                            dismiss()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .tint(palette.accent)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

public struct InstrumentEditorSheet: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let draft: InstrumentDraft
    public let instruments: [Instrument]
    public let onSave: (String, Int, [Int]) -> String?

    @State private var name: String
    @State private var stringCount: Int
    @State private var notes: [Int]
    @State private var baseId: String?
    @State private var error: String? = nil

    public init(draft: InstrumentDraft, instruments: [Instrument], onSave: @escaping (String, Int, [Int]) -> String?) {
        self.draft = draft
        self.instruments = instruments
        self.onSave = onSave
        self._name = State(initialValue: draft.name)
        self._stringCount = State(initialValue: draft.stringCount)
        self._notes = State(initialValue: draft.notes)
        self._baseId = State(initialValue: draft.baseId)
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Instrument name", text: $name)
                        .textInputAutocapitalization(.words)
                        .onChange(of: name) { _, newValue in
                            if newValue.count > maxCustomInstrumentNameLength {
                                name = String(newValue.prefix(maxCustomInstrumentNameLength))
                            }
                            error = nil
                        }

                    Stepper(value: $stringCount, in: minStringCount...maxStringCount) {
                        LabeledContent("Strings", value: "\(stringCount)")
                    }
                    .onChange(of: stringCount) { _, newCount in
                        if notes.count < newCount {
                            let fill = notes.last ?? 40
                            notes.append(contentsOf: repeatElement(fill, count: newCount - notes.count))
                        } else if notes.count > newCount {
                            notes = Array(notes.prefix(newCount))
                        }
                    }
                } header: {
                    Text("Details")
                } footer: {
                    if let error {
                        Text(error)
                            .foregroundStyle(palette.offPitchRed)
                    }
                }

                Section("Start from") {
                    Picker("Template", selection: $baseId) {
                        ForEach(instruments) { inst in
                            Text(inst.label).tag(Optional(inst.id))
                        }
                    }
                    .onChange(of: baseId) { _, next in
                        if let inst = instruments.first(where: { $0.id == next }) {
                            stringCount = inst.stringCount
                            notes = inst.defaultNotes()
                        }
                    }
                }

                let baseNotes = instruments.first(where: { $0.id == baseId })?.defaultNotes()
                Section("Strings") {
                    StringNotesEditor(notes: $notes, baseNotes: baseNotes)
                }
            }
            .navigationTitle(draft.editingId == nil ? "New instrument" : "Edit instrument")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(role: .cancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .confirm) {
                        if let err = onSave(name.trimmingCharacters(in: .whitespacesAndNewlines), stringCount, notes) {
                            error = err
                        } else {
                            dismiss()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .tint(palette.accent)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

public struct ManageInstrumentsSheet: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let instruments: [Instrument]
    public let onEdit: (Instrument) -> Void
    public let onDelete: (Instrument) -> Void

    public var body: some View {
        let custom = instruments.filter(\.isCustom)
        NavigationStack {
            Group {
                if custom.isEmpty {
                    ContentUnavailableView(
                        "No custom instruments",
                        systemImage: "guitars",
                        description: Text("Create one from the tuning picker to add custom string layouts.")
                    )
                } else {
                    List {
                        Section("Custom instruments") {
                            ForEach(custom) { inst in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(inst.label)
                                        .font(.body)
                                    Text("\(inst.stringCount) strings · \(inst.defaultNotes().map { midiNoteLabel($0) }.joined(separator: " "))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) {
                                        onDelete(inst)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                    Button {
                                        dismiss()
                                        onEdit(inst)
                                    } label: {
                                        Label("Edit", systemImage: "pencil")
                                    }
                                    .tint(palette.accent)
                                }
                                .contextMenu {
                                    Button {
                                        dismiss()
                                        onEdit(inst)
                                    } label: {
                                        Label("Edit instrument", systemImage: "pencil")
                                    }
                                    Button(role: .destructive) {
                                        onDelete(inst)
                                    } label: {
                                        Label("Delete instrument", systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Custom instruments")
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

private struct StringNotesEditor: View {
    @Environment(\.omniPalette) private var palette
    @Binding var notes: [Int]
    let baseNotes: [Int]?

    var body: some View {
        ForEach(notes.indices.reversed(), id: \.self) { index in
            let midi = notes[index]
            let diff = baseNotes.flatMap { index < $0.count ? midi - $0[index] : nil }
            Stepper(
                value: Binding(
                    get: { notes[index] },
                    set: { notes[index] = $0 }
                ),
                in: minTunerMidiNote...maxTunerMidiNote
            ) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("String \(notes.count - index)")
                        if let diff, diff != 0 {
                            Text(diff > 0 ? "+\(diff) semitones" : "\(diff) semitones")
                                .font(.caption)
                                .foregroundStyle(palette.accent)
                        }
                    }
                    Spacer()
                    Text(midiNoteLabel(midi))
                        .font(.body.weight(.semibold).monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

public func makeNewInstrumentDraft(instruments: [Instrument]) -> InstrumentDraft {
    InstrumentDraft(
        editingId: nil,
        name: "",
        stringCount: guitarStandardNotes.count,
        notes: guitarStandardNotes,
        baseId: instruments.first(where: { $0.defaultNotes() == guitarStandardNotes })?.id
    )
}
