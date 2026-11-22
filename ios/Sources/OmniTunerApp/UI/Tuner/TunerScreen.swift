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
                        // The panel's own height (the screen less its 12 pt margins) less its 12 pt inset.
                        landscapeBody(state: state, contentHeight: geo.size.height - 24 - 2 * Self.panelInset)
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

    // MARK: - Landscape

    private static let panelInset: CGFloat = 12
    // The share of the readout's width each top corner keeps for its controls (the tuning picker;
    // Auto and Settings); the note may grow into everything between them.
    fileprivate static let wideSideBand: CGFloat = 0.3

    // Landscape: the strings run down the left edge (highest on top) sharing the full height, and
    // the readout takes the rest with the tuning picker, Auto and Settings laid over its top
    // corners. A header strip would cost the note its height for controls that only use the ends.
    // The picker already opens the tuning list, so no unfolded list repeats it here.
    private func landscapeBody(state: TunerChromeState, contentHeight: CGFloat) -> some View {
        HStack(spacing: 0) {
            StringChipsColumn(strings: state.tuning.strings, model: model, height: contentHeight)

            GeometryReader { geo in
                let sideBand = geo.size.width * Self.wideSideBand
                ZStack(alignment: .top) {
                    TunerLiveArea(
                        model: model,
                        isLandscape: true,
                        hasPermission: model.hasPermission,
                        permissionDenied: model.permissionDenied,
                        errorMessage: state.error,
                        area: geo.size
                    )

                    HStack(spacing: 12) {
                        TuningHeaderPicker(instrument: state.instrument, tuning: state.tuning) {
                            pickerOpen = true
                        }
                        // Kept to its side band, so a long tuning name ellipsizes before it can
                        // reach under the note.
                        .frame(maxWidth: sideBand, alignment: .leading)

                        Spacer(minLength: 0)

                        autoToggle(state: state)
                        LandscapeSettingsButton(action: onOpenSettings)
                    }
                    .padding(.leading, 16)
                }
            }
        }
        .padding(Self.panelInset)
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
    /// Landscape: the readout's whole area, which the header's corner controls overlay.
    var area: CGSize = .zero

    // Landscape: the height the overlaid header takes, kept clear by the permission card.
    private static let wideHeaderClearance: CGFloat = 56
    // The direction and numeric lines under the note, with their spacing.
    private static let readoutLinesHeight: CGFloat = 20 + 18 + 12

    var body: some View {
        let live = model.live
        let proAccuracy = model.proAccuracyEnabled
        let inTuneFeedback = model.inTuneFeedbackEnabled

        Group {
            if isLandscape {
                if !hasPermission || errorMessage != nil {
                    permissionCard
                        .frame(maxWidth: 380)
                        .padding(.top, Self.wideHeaderClearance)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    let meterHeight = min(120, max(72, area.height * 0.26))
                    // Landscape is short: the note takes all the height its lines and the meter
                    // leave (a line runs ~1.2 times the font size), and no more width than the band
                    // between the header's corner controls (a note with its octave runs about 1.45
                    // times its size wide, "C#4" being the widest).
                    let byHeight = (area.height - meterHeight - Self.readoutLinesHeight - 8) / 1.2
                    let byWidth = area.width * (1 - 2 * TunerScreen.wideSideBand) / 1.45
                    let noteSize = min(200, max(56, min(byHeight, byWidth)))
                    VStack(spacing: 0) {
                        readoutBlock(
                            live: live,
                            proAccuracy: proAccuracy,
                            inTuneFeedback: inTuneFeedback,
                            noteSize: noteSize
                        )
                        .frame(maxHeight: .infinity)

                        // The meter drops to the bottom edge, level with the lowest string.
                        NeedleMeterView(
                            needlePercent: live.needlePercent,
                            needleColor: needleColor(live: live),
                            cents: live.cents,
                            dimmed: live.holding,
                            showTickLabels: proAccuracy,
                            height: meterHeight,
                            model: model
                        )
                        .padding(.horizontal, 16)
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

    private func readoutBlock(
        live: TunerLiveState,
        proAccuracy: Bool,
        inTuneFeedback: Bool,
        noteSize: CGFloat? = nil
    ) -> some View {
        let displayMidi = live.noteMidi ?? live.targetMidi
        let label = displayMidi.map { midiNoteLabel($0) }
        let (noteName, octave) = splitNoteLabel(label)
        let faded = live.noteMidi == nil && live.targetMidi != nil
        let noteColor = live.tuned ? palette.accent : palette.ink
        // Landscape: at the large size the idle dash reads as a solid bar, so it stays faint.
        let idleDash = label == nil && noteSize != nil
        let noteOpacity = idleDash ? 0.25 : (live.holding || faded ? 0.5 : 1.0)
        let octaveOpacity = live.holding || faded ? 0.45 : 0.75

        let direction = directionText(live: live, inTuneFeedback: inTuneFeedback)
        let directionColor = directionTextColor(live: live)

        return VStack(spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(noteName)
                    .font(.system(size: noteSize ?? 88, weight: .bold).monospacedDigit())
                    .foregroundStyle(noteColor)
                    .opacity(noteOpacity)
                    .scaleEffect(live.pulse ? 1.08 : 1.0)
                if !octave.isEmpty {
                    // Landscape keeps the octave in proportion to the larger note.
                    Text(octave)
                        .font(noteSize.map { .system(size: $0 * 0.4, weight: .semibold).monospacedDigit() }
                            ?? .title.weight(.semibold).monospacedDigit())
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

/// Landscape: the strings stood on end down the panel's edge, highest on top. They share the height
/// the way the upright row shares its width; when they would come out shorter than `minHeight`
/// they split into as few even columns as fit, the highest strings in the first.
private struct StringChipsColumn: View {
    let strings: [NamedFrequency]
    let model: TunerModel
    let height: CGFloat

    private static let spacing: CGFloat = 8
    private static let minHeight: CGFloat = 40
    private static let width: CGFloat = 56

    private func share(_ perColumn: Int) -> CGFloat {
        (height - Self.spacing * CGFloat(perColumn - 1)) / CGFloat(perColumn)
    }

    var body: some View {
        let activeIndex = model.activeIndex
        let tunedStrings = model.tunedStrings
        let count = max(1, strings.count)
        let columns = (1...count).first { n in share((count + n - 1) / n) >= Self.minHeight } ?? count
        let perColumn = (count + columns - 1) / columns
        let chipHeight = max(0, min(Self.width, share(perColumn)))
        let order = Array(strings.indices.reversed())
        let lines = stride(from: 0, to: order.count, by: perColumn).map {
            Array(order[$0..<min($0 + perColumn, order.count)])
        }

        HStack(alignment: .top, spacing: Self.spacing) {
            ForEach(lines.indices, id: \.self) { column in
                // One column spreads its strings over the full height (the spacers carry the gap,
                // so the stack adds none of its own); several stack from the top.
                VStack(spacing: columns == 1 ? 0 : Self.spacing) {
                    ForEach(Array(lines[column].enumerated()), id: \.element) { position, idx in
                        if columns == 1 && position > 0 {
                            Spacer(minLength: Self.spacing)
                        }
                        StringChipButton(
                            name: strings[idx].name,
                            isActive: activeIndex == idx,
                            isTuned: tunedStrings.contains(strings[idx].name),
                            expandWidth: true,
                            height: chipHeight
                        ) {
                            model.selectString(index: idx)
                        }
                        .frame(width: Self.width)
                    }
                }
                .frame(height: height, alignment: .top)
            }
        }
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
    var height: CGFloat = 46
    let action: () -> Void

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
            .frame(minWidth: expandWidth ? height : 56, minHeight: height, maxHeight: height)
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
