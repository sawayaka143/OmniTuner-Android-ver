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
    let onOpenSettings: () -> Void

    @State private var pickerOpen = false
    @State private var tuningDraft: TuningDraft? = nil
    @State private var instrumentDraft: InstrumentDraft? = nil
    @State private var manageInstruments = false

    public init(
        model: TunerModel,
        isActiveTab: Bool,
        onOpenSettings: @escaping () -> Void
    ) {
        self.model = model
        self.isActiveTab = isActiveTab
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        let state = model.chrome
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
        .sensoryFeedback(.success, trigger: model.lockPulseCount) { _, _ in
            model.hapticsEnabled
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
            let s = model.chrome
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
            let s = model.chrome
            TuningEditorSheet(
                draft: draft,
                instrument: s.instrument,
                presets: s.tunings.filter { !$0.isCustom }
            ) { name, notes in
                model.saveCustomTuning(editingId: draft.editingId, name: name, notes: notes)
            }
        }
        .sheet(item: $instrumentDraft) { draft in
            let s = model.chrome
            InstrumentEditorSheet(
                draft: draft,
                instruments: s.instruments
            ) { name, count, notes in
                model.saveCustomInstrument(editingId: draft.editingId, name: name, stringCount: count, notes: notes)
            }
        }
        .sheet(isPresented: $manageInstruments) {
            let s = model.chrome
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

    private func portraitBody(state: TunerChromeState) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .center) {
                TuningHeaderPicker(instrument: state.instrument, tuning: state.tuning) {
                    pickerOpen = true
                }
                .frame(maxWidth: 230, alignment: .leading)

                Spacer()

                autoToggle(state: state)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

            TunerLiveArea(
                model: model,
                isLandscape: false,
                hasPermission: model.hasPermission,
                permissionDenied: model.permissionDenied,
                errorMessage: state.error
            )

            StringChipsRow(strings: state.tuning.strings, model: model)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Landscape (Split: Strings left, Readout+Meter center, Tunings list right)

    private func landscapeBody(state: TunerChromeState) -> some View {
        HStack(spacing: 12) {
            // Left: strings (highest on top)
            StringChipsColumn(strings: state.tuning.strings, model: model)

            // Center: header + readout + meter
            VStack(spacing: 6) {
                HStack {
                    TuningHeaderPicker(instrument: state.instrument, tuning: state.tuning) {
                        pickerOpen = true
                    }
                    Spacer()
                    autoToggle(state: state)
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                Spacer(minLength: 0)

                TunerLiveArea(
                    model: model,
                    isLandscape: true,
                    hasPermission: model.hasPermission,
                    permissionDenied: model.permissionDenied,
                    errorMessage: state.error
                )

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

    private func autoToggle(state: TunerChromeState) -> some View {
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
}

// MARK: - Isolated Live Readout & Meter

private struct TunerLiveArea: View {
    @Environment(\.omniPalette) private var palette

    let model: TunerModel
    let isLandscape: Bool
    let hasPermission: Bool
    let permissionDenied: Bool
    let errorMessage: String?

    var body: some View {
        let live = model.live
        let proAccuracy = model.proAccuracyEnabled
        let inTuneFeedback = model.inTuneFeedbackEnabled

        Group {
            if isLandscape {
                VStack(spacing: 6) {
                    readoutBlock(live: live, proAccuracy: proAccuracy, inTuneFeedback: inTuneFeedback)
                    if !hasPermission || errorMessage != nil {
                        permissionCard
                    } else {
                        NeedleMeterView(
                            needlePercent: live.needlePercent,
                            needleColor: needleColor(live: live),
                            cents: live.cents,
                            dimmed: live.holding,
                            showTickLabels: proAccuracy,
                            model: model
                        )
                    }
                }
            } else {
                VStack(spacing: 0) {
                    Spacer(minLength: 8)
                    readoutBlock(live: live, proAccuracy: proAccuracy, inTuneFeedback: inTuneFeedback)
                    Spacer(minLength: 8)
                    if !hasPermission || errorMessage != nil {
                        permissionCard
                            .padding(.horizontal, 16)
                        Spacer(minLength: 8)
                    } else {
                        NeedleMeterView(
                            needlePercent: live.needlePercent,
                            needleColor: needleColor(live: live),
                            cents: live.cents,
                            dimmed: live.holding,
                            showTickLabels: proAccuracy,
                            model: model
                        )
                        .padding(.horizontal, 16)
                    }
                    Spacer(minLength: 12)
                }
            }
        }
        .onChange(of: model.settingsToken) { _, _ in
            model.refreshFromSettings()
        }
    }

    private func readoutBlock(live: TunerLiveState, proAccuracy: Bool, inTuneFeedback: Bool) -> some View {
        let displayMidi = live.noteMidi ?? live.targetMidi
        let label = displayMidi.map { midiNoteLabel($0) }
        let (noteName, octave) = splitNoteLabel(label)
        let faded = live.noteMidi == nil && live.targetMidi != nil
        let noteColor = live.tuned ? palette.accent : palette.ink
        let noteOpacity = live.holding || faded ? 0.5 : 1.0
        let octaveOpacity = live.holding || faded ? 0.45 : 0.75

        let direction = directionText(live: live, inTuneFeedback: inTuneFeedback)
        let directionColor = directionTextColor(live: live)

        return VStack(spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(noteName)
                    .font(.system(size: 88, weight: .bold).monospacedDigit())
                    .foregroundStyle(noteColor)
                    .opacity(noteOpacity)
                    .scaleEffect(live.pulse ? 1.08 : 1.0)
                if !octave.isEmpty {
                    Text(octave)
                        .font(.title.weight(.semibold).monospacedDigit())
                        .foregroundStyle(noteColor)
                        .opacity(octaveOpacity)
                }
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: live.tuned)
            .animation(.spring(response: 0.25, dampingFraction: 0.85), value: noteOpacity)
            .animation(.spring(response: 0.3, dampingFraction: 0.65), value: live.pulse)

            Text(direction)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(directionColor)
                .opacity(live.holding ? 0.5 : 1.0)
                .frame(minHeight: 20)
                .animation(.easeOut(duration: 0.15), value: directionColor)
                .animation(.spring(response: 0.25, dampingFraction: 0.85), value: live.holding)

            numericReadoutRow(live: live, proAccuracy: proAccuracy)
                .font(.footnote.monospacedDigit())
                .foregroundStyle(palette.mutedLabel)
                .opacity(live.holding ? 0.5 : 1.0)
                .frame(minHeight: 18)
                .animation(.spring(response: 0.25, dampingFraction: 0.85), value: live.holding)
        }
    }

    @ViewBuilder
    private func numericReadoutRow(live: TunerLiveState, proAccuracy: Bool) -> some View {
        if live.status != .idle, live.status != .listening, let c = live.displayCents {
            let value = proAccuracy ? Int(c.rounded()) : Int((c / 100.0).rounded())
            Text(value > 0 ? "+\(value)" : "\(value)")
        } else {
            Text("")
        }
    }

    private var permissionCard: some View {
        VStack(spacing: 10) {
            Image(systemName: "mic.slash.fill")
                .font(.title2)
                .foregroundStyle(palette.offPitchRed)
            Text(errorMessage ?? "Microphone permission is needed to tune your instrument.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.ink)
            if permissionDenied {
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

    private func splitNoteLabel(_ label: String?) -> (String, String) {
        guard let label else { return ("—", "") }
        guard let splitIndex = label.firstIndex(where: { $0 == "-" || ($0.isASCII && $0.isNumber) }) else {
            return (label, "")
        }
        return (String(label[..<splitIndex]), String(label[splitIndex...]))
    }

    private func needleColor(live: TunerLiveState) -> Color {
        if live.tuned { return palette.accent }
        guard let cents = live.cents else { return palette.accent }
        let progress = tuneColorProgress(cents, threshold: Double(live.tolerance))
        return palette.offPitchRed.interpolated(to: palette.accent, fraction: progress)
    }

    private func directionText(live: TunerLiveState, inTuneFeedback: Bool) -> String {
        switch live.status {
        case .idle:
            return "Paused"
        case .listening:
            return "Listening…"
        case .inTune:
            return "IN TUNE"
        case .adjusting:
            guard let c = live.cents else { return "Listening…" }
            if abs(c) <= Double(live.tolerance) {
                return inTuneFeedback ? "" : "IN TUNE"
            }
            return c < 0 ? "TUNE UP" : "TUNE DOWN"
        }
    }

    private func directionTextColor(live: TunerLiveState) -> Color {
        switch live.status {
        case .inTune:
            return palette.accent
        case .adjusting:
            guard let c = live.cents else { return palette.mutedLabel }
            return abs(c) <= Double(live.tolerance) ? palette.accent : palette.offPitchRed
        default:
            return palette.mutedLabel
        }
    }
}

// MARK: - Isolated String Chips

private struct StringChipsRow: View {
    let strings: [NamedFrequency]
    let model: TunerModel

    var body: some View {
        let activeIndex = model.activeIndex
        let tunedStrings = model.tunedStrings
        let fitEvenly = strings.count <= 6

        if fitEvenly {
            HStack(spacing: 8) {
                ForEach(Array(strings.enumerated()), id: \.offset) { idx, string in
                    StringChipButton(
                        name: string.name,
                        isActive: activeIndex == idx,
                        isTuned: tunedStrings.contains(string.name),
                        expandWidth: true
                    ) {
                        model.selectString(index: idx)
                    }
                }
            }
            .padding(.horizontal, 18)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(strings.enumerated()), id: \.offset) { idx, string in
                        StringChipButton(
                            name: string.name,
                            isActive: activeIndex == idx,
                            isTuned: tunedStrings.contains(string.name),
                            expandWidth: false
                        ) {
                            model.selectString(index: idx)
                        }
                    }
                }
                .padding(.horizontal, 18)
            }
        }
    }
}

private struct StringChipsColumn: View {
    let strings: [NamedFrequency]
    let model: TunerModel

    var body: some View {
        let activeIndex = model.activeIndex
        let tunedStrings = model.tunedStrings

        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 8) {
                ForEach(Array(strings.enumerated().reversed()), id: \.offset) { idx, string in
                    StringChipButton(
                        name: string.name,
                        isActive: activeIndex == idx,
                        isTuned: tunedStrings.contains(string.name),
                        expandWidth: false
                    ) {
                        model.selectString(index: idx)
                    }
                }
            }
            .padding(.vertical, 16)
        }
        .frame(width: 72)
        .padding(.leading, 16)
    }
}

private struct StringChipMorphShape: Shape {
    var progress: CGFloat
    let inactiveCornerRadius: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let p = min(1, max(0, progress))
        let h = rect.height
        let w = rect.width + (h - rect.width) * p
        let r = inactiveCornerRadius + (h / 2 - inactiveCornerRadius) * p
        let morphRect = CGRect(
            x: rect.midX - w / 2,
            y: rect.minY,
            width: w,
            height: h
        )
        return RoundedRectangle(cornerRadius: r, style: .continuous).path(in: morphRect)
    }
}

private struct StringChipButton: View {
    @Environment(\.omniPalette) private var palette

    let name: String
    let isActive: Bool
    let isTuned: Bool
    let expandWidth: Bool
    let action: () -> Void

    private static let height: CGFloat = 46
    private static let squircleRadius: CGFloat = 14

    var body: some View {
        let fg: Color = isActive ? palette.onAccent : (isTuned ? palette.accent : palette.ink)
        let shape = StringChipMorphShape(
            progress: isActive ? 1.0 : 0.0,
            inactiveCornerRadius: Self.squircleRadius
        )

        Button(action: action) {
            HStack(spacing: 3) {
                if isTuned && !isActive {
                    Image(systemName: "checkmark")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(palette.accent)
                }
                Text(name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(fg)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: expandWidth ? .infinity : nil)
            .frame(minWidth: expandWidth ? Self.height : 56, minHeight: Self.height, maxHeight: Self.height)
            .background {
                Color.clear
                    .glassEffect(
                        (isActive ? Glass.regular.tint(palette.accent) : Glass.regular).interactive(),
                        in: shape
                    )
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.34, dampingFraction: 0.72), value: isActive)
        .animation(.spring(response: 0.34, dampingFraction: 0.72), value: isTuned)
    }
}
