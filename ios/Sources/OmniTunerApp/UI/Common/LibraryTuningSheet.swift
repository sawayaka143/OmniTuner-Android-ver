import OmniTunerCore
import SwiftUI

public struct LibraryTuningSheet: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let library: LibraryState
    public let onSelect: (String, String) -> Void
    public var onNewTuning: (() -> Void)? = nil
    public var onEditTuning: ((Tuning) -> Void)? = nil
    public var onDeleteTuning: ((Tuning) -> Void)? = nil
    public var onNewInstrument: (() -> Void)? = nil
    public var onManageInstruments: (() -> Void)? = nil

    public init(
        library: LibraryState,
        onSelect: @escaping (String, String) -> Void,
        onNewTuning: (() -> Void)? = nil,
        onEditTuning: ((Tuning) -> Void)? = nil,
        onDeleteTuning: ((Tuning) -> Void)? = nil,
        onNewInstrument: (() -> Void)? = nil,
        onManageInstruments: (() -> Void)? = nil
    ) {
        self.library = library
        self.onSelect = onSelect
        self.onNewTuning = onNewTuning
        self.onEditTuning = onEditTuning
        self.onDeleteTuning = onDeleteTuning
        self.onNewInstrument = onNewInstrument
        self.onManageInstruments = onManageInstruments
    }

    public var body: some View {
        NativeOptionSheet(title: "Instrument & Tuning") {
            if !library.recent.isEmpty {
                Section("Recent") {
                    ForEach(library.recent) { entry in
                        NativeOptionRow(
                            title: "\(entry.instrument.label) · \(entry.tuning.label)",
                            subtitle: entry.tuning.strings.map(\.name).joined(separator: " "),
                            selected: entry.instrument.id == library.instrument.id && entry.tuning.id == library.tuning.id
                        ) {
                            onSelect(entry.instrument.id, entry.tuning.id)
                            dismiss()
                        }
                    }
                }
            }

            ForEach(library.catalog) { entry in
                Section(entry.instrument.label) {
                    ForEach(entry.tunings) { tuning in
                        let editable = tuning.isCustom && entry.instrument.id == library.instrument.id && !entry.instrument.isCustom
                        NativeOptionRow(
                            title: tuning.label,
                            subtitle: tuning.strings.map(\.name).joined(separator: " "),
                            selected: entry.instrument.id == library.instrument.id && tuning.id == library.tuning.id
                        ) {
                            onSelect(entry.instrument.id, tuning.id)
                            dismiss()
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if editable {
                                if let onDeleteTuning {
                                    Button(role: .destructive) {
                                        onDeleteTuning(tuning)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                if let onEditTuning {
                                    Button {
                                        dismiss()
                                        onEditTuning(tuning)
                                    } label: {
                                        Label("Edit", systemImage: "pencil")
                                    }
                                    .tint(palette.accent)
                                }
                            }
                        }
                        .contextMenu {
                            if editable {
                                if let onEditTuning {
                                    Button {
                                        dismiss()
                                        onEditTuning(tuning)
                                    } label: {
                                        Label("Edit tuning", systemImage: "pencil")
                                    }
                                }
                                if let onDeleteTuning {
                                    Button(role: .destructive) {
                                        onDeleteTuning(tuning)
                                    } label: {
                                        Label("Delete tuning", systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if hasCustomActions {
                Section("Custom") {
                    if let onNewTuning, !library.instrument.isCustom {
                        Button {
                            dismiss()
                            onNewTuning()
                        } label: {
                            Label("New tuning for \(library.instrument.label)", systemImage: "plus.circle")
                        }
                    }
                    if let onNewInstrument {
                        Button {
                            dismiss()
                            onNewInstrument()
                        } label: {
                            Label("New instrument", systemImage: "guitars")
                        }
                    }
                    if let onManageInstruments, library.instruments.contains(where: \.isCustom) {
                        Button {
                            dismiss()
                            onManageInstruments()
                        } label: {
                            Label("Manage custom instruments", systemImage: "slider.horizontal.3")
                        }
                    }
                }
            }
        }
    }

    private var hasCustomActions: Bool {
        (onNewTuning != nil && !library.instrument.isCustom) ||
        onNewInstrument != nil ||
        (onManageInstruments != nil && library.instruments.contains(where: \.isCustom))
    }
}
