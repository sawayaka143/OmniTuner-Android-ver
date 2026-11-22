import OmniTunerCore
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public struct ChordFinderScreen: View {
    @Environment(\.omniPalette) private var palette

    @Bindable var model: ChordFinderModel
    let onOpenSettings: () -> Void

    @State private var tuningSheet = false
    @State private var optionsSheet = false
    @State private var copiedFeedback = false

    public init(model: ChordFinderModel, onOpenSettings: @escaping () -> Void) {
        self.model = model
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        let ui = model.ui
        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height
                Group {
                    if isLandscape {
                        landscapeBody(ui: ui)
                    } else {
                        portraitBody(ui: ui)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.paper.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(isLandscape ? .hidden : .visible, for: .navigationBar)
            }
            .toolbar {
                if ui.tab == .voicings && ui.results != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        moreMenu(ui: ui)
                    }
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Settings", systemImage: "gearshape.fill", action: onOpenSettings)
                }
            }
        }
        .tint(palette.accent)
        .onAppear { model.syncTuningIfChanged() }
        .sheet(isPresented: $tuningSheet) {
            LibraryTuningSheet(library: model.ui.library, onSelect: model.selectTuning)
        }
        .sheet(isPresented: $optionsSheet) {
            ChordOptionsSheet(settings: model.ui.settings, model: model)
        }
    }

    // MARK: - Portrait

    private func portraitBody(ui: ChordFinderUiState) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                inputCard(ui: ui, includeOverflow: false)

                if ui.tab == .voicings {
                    if let results = ui.results {
                        if let key = results.key {
                            detectedKeyBanner(key: key, count: results.chords.count, onPlay: model.playProgression)
                        }
                        ForEach(results.chords) { entry in
                            ChordResultCard(
                                entry: entry,
                                tuning: results.tuning,
                                settings: ui.settings,
                                stretchDiagram: false,
                                onPlayShape: model.playShape
                            )
                        }
                    }
                } else {
                    if let key = ui.keyAnalysis.key {
                        KeyFinderResultCard(key: key)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 16)
        }
    }

    // MARK: - Landscape

    private func landscapeBody(ui: ChordFinderUiState) -> some View {
        HStack(spacing: 16) {
            ScrollView {
                VStack(spacing: 12) {
                    inputCard(ui: ui, includeOverflow: true)
                    if ui.tab == .voicings, let results = ui.results, let key = results.key {
                        detectedKeyBanner(key: key, count: results.chords.count, onPlay: model.playProgression)
                    }
                }
                .padding(.vertical, 12)
            }
            .frame(width: 340)
            .padding(.leading, 16)

            Group {
                if ui.tab == .voicings {
                    if let results = ui.results {
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 14) {
                                ForEach(results.chords) { entry in
                                    ChordResultCard(
                                        entry: entry,
                                        tuning: results.tuning,
                                        settings: ui.settings,
                                        stretchDiagram: true,
                                        onPlayShape: model.playShape
                                    )
                                    .frame(width: 360)
                                    .frame(maxHeight: .infinity)
                                }
                            }
                            .scrollTargetLayout()
                            .padding(.trailing, 16)
                        }
                        .scrollTargetBehavior(.viewAligned)
                        .padding(.vertical, 12)
                    } else {
                        emptyPane(title: "Enter a chord progression", subtitle: "Tap Generate or Shuffle to find playable shapes.")
                    }
                } else {
                    ScrollView {
                        VStack {
                            if let key = ui.keyAnalysis.key {
                                KeyFinderResultCard(key: key)
                            } else {
                                emptyPane(title: "No chords detected yet", subtitle: "Type chords on the left to identify the key.")
                            }
                        }
                        .padding(.vertical, 12)
                        .padding(.trailing, 16)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func modePicker(ui: ChordFinderUiState) -> some View {
        Picker("Mode", selection: Binding(
            get: { ui.tab },
            set: { model.selectTab($0) }
        )) {
            Text("Voicings").tag(ChordsTab.voicings)
            Text("Key finder").tag(ChordsTab.keyFinder)
        }
        .pickerStyle(.segmented)
    }

    // MARK: - Input Cards

    private func inputCard(ui: ChordFinderUiState, includeOverflow: Bool) -> some View {
        PedalCard {
            VStack(alignment: .leading, spacing: 12) {
                modePicker(ui: ui)

                if ui.tab == .voicings {
                    voicingsContent(ui: ui, includeOverflow: includeOverflow)
                } else {
                    keyFinderContent(ui: ui)
                }
            }
            .padding(16)
        }
        .overlay(alignment: .bottom) {
            if ui.tab == .voicings && ui.generating {
                ProgressView()
                    .progressViewStyle(.linear)
                    .tint(palette.accent)
                    .padding(.horizontal, 20)
                    .offset(y: 10)
            }
        }
    }

    @ViewBuilder
    private func voicingsContent(ui: ChordFinderUiState, includeOverflow: Bool) -> some View {
        HStack(spacing: 12) {
                    TuningHeaderPicker(instrument: ui.library.instrument, tuning: ui.library.tuning) {
                        tuningSheet = true
                    }
                    Spacer()
                    if includeOverflow && ui.results != nil {
                        moreMenu(ui: ui, inCard: true)
                            .buttonStyle(.glass)
                            .buttonBorderShape(.circle)
                            .controlSize(.regular)
                    }
                    OptionsButton(label: "Chord options") {
                        optionsSheet = true
                    }
                    if includeOverflow {
                        LandscapeSettingsButton(action: onOpenSettings)
                    }
                }

                TextField("C, G, Am, F", text: Binding(
                    get: { ui.progressionInput },
                    set: { model.setProgressionInput($0) }
                ))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .onSubmit { model.generate() }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.paper)
                )

                HStack {
                    Text("\(ui.progressionCount.readable)/\(ui.progressionCount.total) chords")
                        .font(.caption)
                        .foregroundStyle(palette.mutedLabel)
                    Spacer()
                    Text("Key: \(pcName(ui.tonicPc, flats: flatsForPc(ui.tonicPc)))")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(palette.mutedLabel)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(0..<12, id: \.self) { pc in
                            let selected = pc == ui.tonicPc
                            Button {
                                model.setTonic(pc)
                            } label: {
                                Text(pcName(pc, flats: flatsForPc(pc)))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(selected ? palette.onAccent : palette.ink)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Capsule(style: .continuous).fill(selected ? palette.accent : palette.paper))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                HStack(spacing: 10) {
                    Button(action: model.shuffle) {
                        Label("Shuffle", systemImage: "shuffle")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.large)

                    Button(action: model.generate) {
                        Text("Generate")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(palette.accent)
                    .controlSize(.large)
                    .disabled(ui.progressionCount.total == 0)
                }
    }

    @ViewBuilder
    private func keyFinderContent(ui: ChordFinderUiState) -> some View {
        Text("Analyze chords")
            .font(.headline)
            .foregroundStyle(palette.ink)

        TextField("Dm7, G7, Cmaj7, A7", text: Binding(
            get: { ui.keyInput },
            set: { model.setKeyInput($0) }
        ))
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(palette.paper)
        )

        Text("\(ui.keyAnalysis.count.readable) of \(ui.keyAnalysis.count.total) chords recognized")
            .font(.caption)
            .foregroundStyle(palette.mutedLabel)
    }

    private func detectedKeyBanner(key: DetectedKey, count: Int, onPlay: @escaping () -> Void) -> some View {
        PedalCard {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Detected key")
                        .font(.caption)
                        .foregroundStyle(palette.mutedLabel)
                    Text(keyName(tonicName: key.tonicName, mode: key.mode))
                        .font(.headline)
                        .foregroundStyle(palette.ink)
                }
                Spacer()
                if count > 0 {
                    Button(action: onPlay) {
                        Label("Play all", systemImage: "play.fill")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.glassProminent)
                    .tint(palette.accent)
                    .controlSize(.small)
                }
            }
            .padding(14)
        }
    }

    private func moreMenu(ui: ChordFinderUiState, inCard: Bool = false) -> some View {
        Menu {
            Button {
                if let text = model.tabText() {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = text
                    #endif
                    copiedFeedback = true
                    Task {
                        try? await Task.sleep(for: .seconds(1.2))
                        copiedFeedback = false
                    }
                }
            } label: {
                Label(copiedFeedback ? "Copied tab" : "Copy tab", systemImage: copiedFeedback ? "checkmark" : "doc.on.doc")
            }

            Button(role: .destructive) {
                model.clear()
            } label: {
                Label("Clear results", systemImage: "xmark.circle")
            }
        } label: {
            if inCard {
                Image(systemName: copiedFeedback ? "checkmark" : "ellipsis")
                    .font(.system(size: 19, weight: .medium))
                    .frame(width: 29, height: 29)
                    .accessibilityLabel("More")
            } else {
                Label("More", systemImage: copiedFeedback ? "checkmark" : "ellipsis")
                    .labelStyle(.iconOnly)
            }
        }
    }

    private func emptyPane(title: String, subtitle: String) -> some View {
        ContentUnavailableView(title, systemImage: "music.quarternote.3", description: Text(subtitle))
    }
}

// MARK: - Chord Result Card

private struct ChordResultCard: View {
    @Environment(\.omniPalette) private var palette
    let entry: ChordEntry
    let tuning: ParsedTuning
    let settings: ChordSettings
    let stretchDiagram: Bool
    let onPlayShape: (VoicingShape) -> Void

    @State private var shapeIndex = 0

    var body: some View {
        PedalCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Text(entry.chord?.symbol ?? entry.token)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(palette.ink)

                    if let badge = entry.badge {
                        Text(badge.numeral)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(badgeForeground(badge.kind))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule(style: .continuous).fill(badgeColor(badge.kind).opacity(0.16)))
                    }

                    Spacer()

                    if !entry.shapes.isEmpty {
                        let clamped = min(shapeIndex, entry.shapes.count - 1)
                        HStack(spacing: 6) {
                            Button {
                                shapeIndex = max(0, clamped - 1)
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.caption.weight(.bold))
                                    .frame(width: 28, height: 28)
                                    .background(Circle().fill(palette.paper))
                            }
                            .buttonStyle(.plain)
                            .disabled(clamped == 0)

                            Text("\(clamped + 1) / \(entry.shapes.count)")
                                .font(.caption.weight(.semibold).monospacedDigit())
                                .foregroundStyle(palette.mutedLabel)

                            Button {
                                shapeIndex = min(entry.shapes.count - 1, clamped + 1)
                            } label: {
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                                    .frame(width: 28, height: 28)
                                    .background(Circle().fill(palette.paper))
                            }
                            .buttonStyle(.plain)
                            .disabled(clamped >= entry.shapes.count - 1)
                        }

                        Button {
                            onPlayShape(entry.shapes[clamped])
                        } label: {
                            Image(systemName: "play.fill")
                                .font(.caption.weight(.bold))
                        }
                        .buttonStyle(.glassProminent)
                        .tint(palette.accent)
                        .buttonBorderShape(.circle)
                        .controlSize(.small)
                    }
                }

                if let chord = entry.chord {
                    Text(ChordFinderModel.chordTonesLabel(chord: chord, flats: chord.flats || tuning.flats))
                        .font(.caption)
                        .foregroundStyle(palette.mutedLabel)

                    if entry.shapes.isEmpty {
                        Text("No playable voicing found on this tuning.")
                            .font(.subheadline)
                            .foregroundStyle(palette.mutedLabel)
                            .padding(.vertical, 12)
                    } else {
                        let clamped = min(shapeIndex, entry.shapes.count - 1)
                        let shape = entry.shapes[clamped]
                        if settings.view == .fretboard {
                            NeckDiagramView(
                                frets: shape.frets,
                                tuning: tuning,
                                symbol: chord.symbol,
                                rootPc: chord.rootPc,
                                index: clamped,
                                labels: settings.labels,
                                flats: chord.flats || tuning.flats,
                                stretch: true
                            )
                            .frame(maxWidth: .infinity)
                            .frame(height: stretchDiagram ? nil : 210)
                            .onTapGesture { onPlayShape(shape) }
                        } else {
                            VStack(alignment: .leading, spacing: 3) {
                                ForEach(ChordFinderModel.tabRows(shape: shape, tuning: tuning)) { row in
                                    let left = String(repeating: "-", count: row.padLeft)
                                    let right = String(repeating: "-", count: row.padRight)
                                    Text("\(row.label)|\(left)\(row.symbol)\(right)|")
                                        .font(.system(.callout, design: .monospaced))
                                        .foregroundStyle(row.muted ? palette.mutedLabel : palette.ink)
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.paper))
                        }
                    }
                } else {
                    Text(entry.error ?? "Invalid chord")
                        .font(.subheadline)
                        .foregroundStyle(palette.offPitchRed)
                }
            }
            .padding(16)
        }
        .onChange(of: entry.shapes.count) { _, newCount in
            if shapeIndex >= newCount { shapeIndex = max(0, newCount - 1) }
        }
    }

    private func badgeColor(_ kind: BadgeKind) -> Color {
        switch kind {
        case .good: palette.goodGreen
        case .warn: palette.warnAmber
        case .borrowed: palette.infoViolet
        case .bad: palette.offPitchRed
        }
    }

    private func badgeForeground(_ kind: BadgeKind) -> Color {
        badgeColor(kind)
    }
}

private struct KeyFinderResultCard: View {
    @Environment(\.omniPalette) private var palette
    let key: DetectedKey

    var body: some View {
        PedalCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Best match")
                            .font(.caption)
                            .foregroundStyle(palette.mutedLabel)
                        Text(keyName(tonicName: key.tonicName, mode: key.mode))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(palette.ink)
                    }
                    Spacer()
                    Text(key.confidence.capitalized)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(confidenceColor(key.confidence))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Capsule(style: .continuous).fill(confidenceColor(key.confidence).opacity(0.16)))
                }

                Text("\(Int((key.coverage * 100).rounded()))% diatonic coverage")
                    .font(.subheadline)
                    .foregroundStyle(palette.mutedLabel)

                if !key.alternatives.isEmpty {
                    Divider().overlay(palette.hairline)
                    Text("Alternatives")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.mutedLabel)
                    ForEach(Array(key.alternatives.enumerated()), id: \.offset) { _, alt in
                        HStack {
                            Text(keyName(tonicName: alt.tonicName, mode: alt.mode))
                                .font(.body)
                                .foregroundStyle(palette.ink)
                            Spacer()
                            Text("\(Int((alt.coverage * 100).rounded()))%")
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(palette.mutedLabel)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    private func confidenceColor(_ confidence: String) -> Color {
        switch confidence {
        case "strong": palette.goodGreen
        case "moderate": palette.accent
        default: palette.warnAmber
        }
    }
}

private struct ChordOptionsSheet: View {
    let settings: ChordSettings
    let model: ChordFinderModel

    var body: some View {
        NativeFormSheet(title: "Chord Options") {
            Section("Voicings") {
                Stepper(
                    value: Binding(
                        get: { settings.voicingCount },
                        set: { model.changeVoicingCount($0 - settings.voicingCount) }
                    ),
                    in: minVoicingCount...maxVoicingCount
                ) {
                    LabeledContent("Shapes per chord", value: "\(settings.voicingCount)")
                }
            }

            Section("Display") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("View")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Picker("View", selection: Binding(
                        get: { settings.view },
                        set: { model.setView($0) }
                    )) {
                        ForEach(VoicingViewMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Labels")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Picker("Labels", selection: Binding(
                        get: { settings.labels },
                        set: { model.setLabels($0) }
                    )) {
                        ForEach(DiagramLabels.allCases) { label in
                            Text(label.label).tag(label)
                        }
                    }
                    .pickerStyle(.segmented)
                }
            }
        }
    }
}
