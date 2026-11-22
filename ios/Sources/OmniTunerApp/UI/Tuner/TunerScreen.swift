import OmniTunerCore
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public struct TunerScreen: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.scenePhase) private var scenePhase

    @Bindable var model: TunerModel
    let isActiveTab: Bool
    let isSelectedTab: Bool
    let onOpenSettings: () -> Void

    @State private var pickerOpen = false
    @State private var tuningDraft: TuningDraft? = nil
    @State private var instrumentDraft: InstrumentDraft? = nil
    @State private var manageInstruments = false
    @State private var panelVisible: Bool = true

    public init(
        model: TunerModel,
        isActiveTab: Bool,
        isSelectedTab: Bool? = nil,
        onOpenSettings: @escaping () -> Void
    ) {
        self.model = model
        self.isActiveTab = isActiveTab
        self.isSelectedTab = isSelectedTab ?? isActiveTab
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        let state = model.ui
        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height

                PedalPanel {
                    if isLandscape {
                        landscapeBody(state: state)
                    } else {
                        portraitBody(state: state)
                    }
                }
                .scaleEffect(panelVisible ? 1.0 : 0.94)
                .opacity(panelVisible ? 1.0 : 0.0)
                .padding(.horizontal, 16)
                .padding(.top, isLandscape ? 12 : 4)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.paper.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(isLandscape ? .hidden : .visible, for: .navigationBar)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Settings", systemImage: "gearshape.fill", action: onOpenSettings)
                }
            }
        }
        .tint(palette.accent)
        .onChange(of: isSelectedTab, initial: true) { _, selected in
            if selected {
                panelVisible = false
                withAnimation(.spring(response: 0.36, dampingFraction: 0.82)) {
                    panelVisible = true
                }
            } else {
                var t = Transaction()
                t.disablesAnimations = true
                withTransaction(t) {
                    panelVisible = false
                }
            }
        }
        .task(id: "\(isActiveTab)|\(scenePhase == .active)") {
            if isActiveTab && scenePhase == .active {
                await Task.yield()
                await model.ensurePermissionAndStart()
            } else {
                model.stopCapture()
            }
        }
        .onDisappear {
            model.stopCapture()
        }
        .sheet(isPresented: $pickerOpen) {
            let s = model.ui
            LibraryTuningSheet(
                library: LibraryState(
                    instruments: s.instruments,
                    instrument: s.instrument,
                    tunings: s.tunings,
                    tuning: s.tuning,
                    catalog: s.catalog,
                    recent: s.recent
                ),
                onSelect: model.selectTuning,
                onNewTuning: {
                    tuningDraft = TuningDraft(
                        editingId: nil,
                        name: "",
                        notes: s.tuning.midiNotes(),
                        baseId: s.tuning.id
                    )
                },
                onEditTuning: { tuning in
                    tuningDraft = TuningDraft(
                        editingId: tuning.id,
                        name: tuning.label,
                        notes: tuning.midiNotes(),
                        baseId: s.tunings.first?.id
                    )
                },
                onDeleteTuning: { tuning in
                    model.deleteCustomTuning(tuning.id)
                },
                onNewInstrument: {
                    instrumentDraft = makeNewInstrumentDraft(instruments: s.instruments)
                },
                onManageInstruments: {
                    manageInstruments = true
                }
            )
        }
        .sheet(item: $tuningDraft) { draft in
            let s = model.ui
            TuningEditorSheet(
                draft: draft,
                instrument: s.instrument,
                presets: s.tunings.filter { !$0.isCustom }
            ) { name, notes in
                model.saveCustomTuning(editingId: draft.editingId, name: name, notes: notes)
            }
        }
        .sheet(item: $instrumentDraft) { draft in
            let s = model.ui
            InstrumentEditorSheet(
                draft: draft,
                instruments: s.instruments
            ) { name, count, notes in
                model.saveCustomInstrument(editingId: draft.editingId, name: name, stringCount: count, notes: notes)
            }
        }
        .sheet(isPresented: $manageInstruments) {
            let s = model.ui
            ManageInstrumentsSheet(
                instruments: s.instruments,
                onEdit: { inst in
                    instrumentDraft = InstrumentDraft(
                        editingId: inst.id,
                        name: inst.label,
                        stringCount: inst.stringCount,
                        notes: inst.defaultNotes(),
                        baseId: s.instruments.first?.id
                    )
                },
                onDelete: { inst in
                    model.deleteCustomInstrument(inst.id)
                }
            )
        }
    }

    // MARK: - Portrait

    private func portraitBody(state: TunerUiState) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .center) {
                TuningHeaderPicker(instrument: state.instrument, tuning: state.tuning) {
                    pickerOpen = true
                }
                .frame(maxWidth: 230, alignment: .leading)

                Spacer()

                autoToggle(state: state)
            }
            .padding(.leading, 14)
            .padding(.trailing, 24)
            .padding(.top, 16)

            Spacer(minLength: 8)

            readoutBlock(state: state)

            Spacer(minLength: 8)

            if !model.hasPermission || state.error != nil {
                permissionCard(state: state)
                    .padding(.horizontal, 16)
                Spacer(minLength: 8)
            } else {
                NeedleMeterView(
                    needlePercent: state.needlePercent,
                    needleColor: needleColor(state: state),
                    cents: state.cents,
                    dimmed: state.holding,
                    inputLevel: model.inputLevel
                )
                .padding(.horizontal, 16)
            }

            Spacer(minLength: 12)

            stringChipsRow(state: state)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Landscape (Split: Strings left, Readout+Meter center, Tunings list right)

    private func landscapeBody(state: TunerUiState) -> some View {
        HStack(spacing: 12) {
            // Left: strings (highest on top)
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 8) {
                    ForEach(Array(state.tuning.strings.enumerated().reversed()), id: \.offset) { idx, string in
                        stringChip(index: idx, string: string, state: state)
                    }
                }
                .padding(.vertical, 16)
            }
            .frame(width: 72)
            .padding(.leading, 16)

            // Center: header + readout + meter
            VStack(spacing: 6) {
                HStack {
                    TuningHeaderPicker(instrument: state.instrument, tuning: state.tuning) {
                        pickerOpen = true
                    }
                    Spacer()
                    autoToggle(state: state)
                }
                .padding(.horizontal, 12)
                .padding(.top, 18)

                Spacer(minLength: 0)

                readoutBlock(state: state)

                if !model.hasPermission || state.error != nil {
                    permissionCard(state: state)
                } else {
                    NeedleMeterView(
                        needlePercent: state.needlePercent,
                        needleColor: needleColor(state: state),
                        cents: state.cents,
                        dimmed: state.holding,
                        inputLevel: model.inputLevel
                    )
                }

                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            // Right: unfolded tunings list + Settings
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(state.instrument.label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.mutedLabel)
                    Spacer()
                    LandscapeSettingsButton(action: onOpenSettings)
                }

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 4) {
                        ForEach(state.tunings) { tuning in
                            let selected = tuning.id == state.tuning.id
                            Button {
                                model.selectTuning(instrumentId: state.instrument.id, tuningId: tuning.id)
                            } label: {
                                HStack {
                                    Text(tuning.label)
                                        .font(.subheadline.weight(selected ? .semibold : .regular))
                                        .foregroundStyle(selected ? palette.accent : palette.ink)
                                        .lineLimit(1)
                                    Spacer()
                                    if selected {
                                        Image(systemName: "checkmark")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(palette.accent)
                                    }
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(selected ? palette.paper : Color.clear)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .frame(width: 170)
            .padding(.top, 18)
            .padding(.bottom, 16)
            .padding(.trailing, 18)
        }
    }

    // MARK: - Subviews

    private func autoToggle(state: TunerUiState) -> some View {
        HStack(spacing: 6) {
            Text("Auto")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(palette.ink)
            Toggle("Auto", isOn: Binding(
                get: { state.mode == .auto },
                set: { model.selectMode($0 ? .auto : .manual) }
            ))
            .labelsHidden()
            .tint(palette.accent)
        }
        .padding(.vertical, 6)
    }

    private func readoutBlock(state: TunerUiState) -> some View {
        let displayMidi = state.noteMidi ?? state.targetMidi
        let label = displayMidi.map { midiNoteLabel($0) }
        let noteName = label.map { String($0.dropLast()) } ?? "—"
        let octave = label.map { String($0.suffix(1)) } ?? ""
        let faded = state.noteMidi == nil && state.targetMidi != nil

        return VStack(spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(noteName)
                    .font(.system(size: 88, weight: .bold).monospacedDigit())
                    .foregroundStyle(noteColor(state: state).opacity(state.holding || faded ? 0.5 : 1.0))
                    .scaleEffect(state.pulse ? 1.08 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.65), value: state.pulse)
                if !octave.isEmpty {
                    Text(octave)
                        .font(.title.weight(.semibold).monospacedDigit())
                        .foregroundStyle(noteColor(state: state).opacity(state.holding || faded ? 0.45 : 0.75))
                }
            }

            Text(statusLine(state: state))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(statusColor(state: state))

            Text(hzDisplay(state.frequency))
                .font(.footnote.monospacedDigit())
                .foregroundStyle(palette.mutedLabel)
        }
    }

    private func stringChipsRow(state: TunerUiState) -> some View {
        let strings = state.tuning.strings
        let fitEvenly = strings.count <= 6
        return Group {
            if fitEvenly {
                HStack(spacing: 8) {
                    ForEach(Array(strings.enumerated()), id: \.offset) { idx, string in
                        stringChip(index: idx, string: string, state: state, expandWidth: true)
                    }
                }
                .padding(.horizontal, 18)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(strings.enumerated()), id: \.offset) { idx, string in
                            stringChip(index: idx, string: string, state: state, expandWidth: false)
                        }
                    }
                    .padding(.horizontal, 18)
                }
            }
        }
    }

    @ViewBuilder
    private func stringChip(index: Int, string: NamedFrequency, state: TunerUiState, expandWidth: Bool = false) -> some View {
        let isActive = state.activeIndex == index
        let isTuned = state.tunedStrings.contains(string.name)
        let fg: Color = isActive ? palette.onAccent : (isTuned ? palette.accent : palette.ink)

        let label = HStack(spacing: 3) {
            if isTuned && !isActive {
                Image(systemName: "checkmark")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(palette.accent)
            }
            Text(string.name)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(fg)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: expandWidth ? .infinity : nil)
        .frame(minWidth: expandWidth ? 0 : 44, minHeight: 34)

        if isActive {
            Button {
                model.selectString(index: index)
            } label: {
                label
            }
            .buttonStyle(.glassProminent)
            .tint(palette.accent)
            .buttonBorderShape(.capsule)
        } else {
            Button {
                model.selectString(index: index)
            } label: {
                label
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.roundedRectangle(radius: 14))
        }
    }

    private func permissionCard(state: TunerUiState) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "mic.slash.fill")
                .font(.title2)
                .foregroundStyle(palette.offPitchRed)
            Text(state.error ?? "Microphone permission is needed to tune your instrument.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.ink)
            if model.permissionDenied {
                Button("Open Settings") {
                    #if canImport(UIKit)
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                    #endif
                }
                .buttonStyle(.glassProminent)
            } else {
                Button("Allow Microphone") {
                    Task { await model.ensurePermissionAndStart() }
                }
                .buttonStyle(.glassProminent)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.paper)
        )
    }

    // MARK: - Color & Status Helpers

    private func needleColor(state: TunerUiState) -> Color {
        if state.tuned { return palette.accent }
        guard let cents = state.cents else { return palette.accent }
        let progress = tuneColorProgress(cents, threshold: Double(state.tolerance))
        return palette.offPitchRed.interpolated(to: palette.accent, fraction: progress)
    }

    private func noteColor(state: TunerUiState) -> Color {
        if state.tuned { return palette.accent }
        return palette.ink
    }

    private func statusColor(state: TunerUiState) -> Color {
        switch state.status {
        case .inTune: return palette.accent
        case .adjusting:
            guard let c = state.cents else { return palette.mutedLabel }
            return abs(c) <= Double(state.tolerance) ? palette.accent : palette.offPitchRed
        default: return palette.mutedLabel
        }
    }

    private func statusLine(state: TunerUiState) -> String {
        switch state.status {
        case .idle:
            return "Paused"
        case .listening:
            return "Listening…"
        case .inTune:
            if state.proAccuracy, let c = state.cents {
                return String(format: "IN TUNE (%+.1f¢)", c)
            }
            return "IN TUNE"
        case .adjusting:
            guard let c = state.cents else { return "Listening…" }
            let dir = tuneDirectionText(c, threshold: Double(state.tolerance))
            if state.proAccuracy {
                return String(format: "%@ (%+.1f¢)", dir, c)
            }
            let centsTxt = tuneCentsText(c, threshold: Double(state.tolerance))
            return centsTxt.isEmpty ? dir : "\(dir) · \(centsTxt)"
        }
    }
}
