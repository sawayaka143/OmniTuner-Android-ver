import OmniTunerCore
import SwiftUI

public struct ScalesScreen: View {
    @Environment(\.omniPalette) private var palette

    @Bindable var model: ScalesModelView
    let onOpenSettings: () -> Void

    @State private var scaleSheet = false
    @State private var optionsSheet = false
    @State private var tuningSheet = false

    public init(model: ScalesModelView, onOpenSettings: @escaping () -> Void) {
        self.model = model
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        let ui = model.ui
        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height
                let screenCardHeight = max(240, geo.size.height - 28)

                ScrollView {
                    VStack(spacing: 16) {
                        scaleMapCard(ui: ui, fitHeight: isLandscape ? screenCardHeight : nil, includeSettings: isLandscape)

                        if isLandscape {
                            HStack(alignment: .top, spacing: 16) {
                                scaleControlsCard(ui: ui, showScaleRow: true)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                                instrumentCard(ui: ui)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                            }
                            .fixedSize(horizontal: false, vertical: true)
                        } else {
                            scaleControlsCard(ui: ui, showScaleRow: false)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, isLandscape ? 12 : 4)
                    .padding(.bottom, 16)
                }
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
        .sheet(isPresented: $scaleSheet) {
            ScalePickerSheet(ui: model.ui, model: model)
        }
        .sheet(isPresented: $optionsSheet) {
            ScaleOptionsSheet(settings: model.ui.settings, model: model)
        }
        .sheet(isPresented: $tuningSheet) {
            LibraryTuningSheet(library: model.ui.library, onSelect: model.selectTuning)
        }
    }

    // MARK: - Cards

    private func scaleMapCard(ui: ScalesUiState, fitHeight: CGFloat?, includeSettings: Bool) -> some View {
        let settings = ui.settings
        let flats = settings.accidental == .flat
        let scale = allScales.first { $0.id == settings.scaleId } ?? allScales[0]
        let title = "\(noteName(pitchClass: settings.rootPitchClass, preferFlats: flats)) \(scale.label)"
        let fitted = fitHeight != nil

        return PedalCard {
            VStack(spacing: fitted ? 10 : 14) {
                HStack(spacing: 12) {
                    HeaderPicker(caption: "Scale", value: title) {
                        scaleSheet = true
                    }
                    Spacer(minLength: 4)
                    TuningHeaderPicker(instrument: ui.instrument, tuning: ui.tuning) {
                        tuningSheet = true
                    }
                    if fitted {
                        playButton(isPlaying: ui.isPlaying, compact: true)
                    }
                    OptionsButton(label: "Display options") {
                        optionsSheet = true
                    }
                    if includeSettings {
                        LandscapeSettingsButton(action: onOpenSettings)
                    }
                }

                FretboardView(
                    board: ui.board,
                    useNoteNames: settings.labelMode == .noteNames,
                    showOutside: settings.showOutsideScale,
                    sounding: ui.sounding,
                    fitHeight: fitted,
                    onCellTap: model.playCell
                )
                .frame(maxHeight: fitted ? .infinity : nil)

                if !fitted {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(ui.tones) { tone in
                                let isSounding = ui.sounding?.midi == tone.midi
                                Button {
                                    model.playTone(midi: tone.midi)
                                } label: {
                                    VStack(spacing: 1) {
                                        Text(tone.noteName)
                                            .font(.subheadline.weight(.bold))
                                        Text(tone.intervalLabel)
                                            .font(.caption2)
                                            .opacity(0.75)
                                    }
                                    .foregroundStyle(isSounding ? palette.onAccent : (tone.isRoot ? palette.accent : palette.ink))
                                    .frame(minWidth: 46, minHeight: 44)
                                    .padding(.horizontal, 6)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(isSounding ? palette.accent : palette.paper)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(tone.isRoot && !isSounding ? palette.accent : Color.clear, lineWidth: 1.5)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    playButton(isPlaying: ui.isPlaying, compact: false)
                }
            }
            .padding(16)
        }
        .frame(height: fitHeight)
    }

    private func scaleControlsCard(ui: ScalesUiState, showScaleRow: Bool) -> some View {
        let flats = ui.settings.accidental == .flat
        let labels = flats ? flatNames : sharpNames
        let scale = allScales.first { $0.id == ui.settings.scaleId } ?? allScales[0]

        return PedalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Root")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.mutedLabel)

                Picker("Root", selection: Binding(
                    get: { ui.settings.rootPitchClass },
                    set: { model.setRootPitchClass($0) }
                )) {
                    ForEach(0..<labels.count, id: \.self) { idx in
                        Text(labels[idx])
                            .font(.title3.weight(.semibold))
                            .tag(idx)
                    }
                }
                .pickerStyle(.wheel)
                .frame(height: 112)
                .clipped()

                if showScaleRow {
                    Divider().overlay(palette.hairline)
                    Button {
                        scaleSheet = true
                    } label: {
                        HStack {
                            Text("Scale")
                                .foregroundStyle(palette.ink)
                            Spacer()
                            Text(scale.label)
                                .foregroundStyle(palette.accent)
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(palette.mutedLabel)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
    }

    private func instrumentCard(ui: ScalesUiState) -> some View {
        PedalCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Instrument")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.mutedLabel)

                Button {
                    tuningSheet = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(ui.instrument.label)
                                .font(.headline)
                                .foregroundStyle(palette.ink)
                            Text("\(ui.tuning.label) · \(ui.tuning.strings.map(\.name).joined(separator: " "))")
                                .font(.caption)
                                .foregroundStyle(palette.mutedLabel)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(palette.mutedLabel)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
    }

    private func playButton(isPlaying: Bool, compact: Bool) -> some View {
        Button(action: model.togglePlayScale) {
            Label(isPlaying ? "Stop" : "Play scale", systemImage: isPlaying ? "stop.fill" : "play.fill")
                .font(compact ? .subheadline.weight(.semibold) : .headline)
                .frame(maxWidth: compact ? nil : .infinity)
        }
        .buttonStyle(.glassProminent)
        .tint(palette.accent)
        .controlSize(compact ? .small : .large)
    }
}

private struct ScalePickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let ui: ScalesUiState
    let model: ScalesModelView

    private var groups: [(String, [Scale])] {
        var order: [String] = []
        var dict: [String: [Scale]] = [:]
        for scale in allScales {
            if dict[scale.group] == nil { order.append(scale.group) }
            dict[scale.group, default: []].append(scale)
        }
        return order.map { ($0, dict[$0] ?? []) }
    }

    var body: some View {
        NativeOptionSheet(title: "Scale") {
            ForEach(groups, id: \.0) { groupName, items in
                Section(groupName) {
                    ForEach(items) { scale in
                        NativeOptionRow(
                            title: scale.label,
                            selected: scale.id == ui.settings.scaleId
                        ) {
                            model.setScaleId(scale.id)
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

private struct ScaleOptionsSheet: View {
    let settings: ScaleSettings
    let model: ScalesModelView

    var body: some View {
        NativeFormSheet(title: "Scale Options") {
            Section("Fretboard") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Accidentals")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Picker("Accidentals", selection: Binding(
                        get: { settings.accidental },
                        set: { model.setAccidental($0) }
                    )) {
                        ForEach(Accidental.allCases) { opt in
                            Text(opt.label).tag(opt)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Frets")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Picker("Frets", selection: Binding(
                        get: { settings.fretCount },
                        set: { model.setFretCount($0) }
                    )) {
                        ForEach(fretCountOptions, id: \.self) { count in
                            Text("\(count)").tag(count)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Labels")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Picker("Labels", selection: Binding(
                        get: { settings.labelMode },
                        set: { model.setLabelMode($0) }
                    )) {
                        ForEach(LabelMode.allCases) { opt in
                            Text(opt.label).tag(opt)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Toggle("Show notes outside scale", isOn: Binding(
                    get: { settings.showOutsideScale },
                    set: { model.setShowOutsideScale($0) }
                ))
            }
        }
    }
}
