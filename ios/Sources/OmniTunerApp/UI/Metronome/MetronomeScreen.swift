import OmniTunerCore
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

public struct MetronomeScreen: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.scenePhase) private var scenePhase

    @Bindable var model: MetronomeModelView
    let onOpenSettings: () -> Void

    @State private var meterSheet = false
    @State private var presetsSheet = false
    @State private var optionsSheet = false
    @State private var savePresetDialog = false
    @State private var bpmDialog = false
    @State private var bpmText = ""
    @State private var presetNameText = ""

    @State private var wheelOpen = false
    @State private var wheelBusy = false
    @State private var bpmScale: CGFloat = 1.0
    @State private var tapFlash = false
    @State private var tapFlashTask: Task<Void, Never>? = nil

    public init(model: MetronomeModelView, onOpenSettings: @escaping () -> Void) {
        self.model = model
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        let ui = model.ui
        let bpmInt = Int(ui.state.bpm.rounded())
        let beat = currentBeat(for: ui)

        NavigationStack {
            GeometryReader { geo in
                let isLandscape = geo.size.width > geo.size.height
                PedalPanel {
                    if isLandscape {
                        landscapeBody(ui: ui)
                    } else {
                        portraitBody(ui: ui)
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
        .onChange(of: scenePhase) { _, phase in
            model.setBackground(phase != .active)
        }
        .task(id: WheelFoldKey(open: wheelOpen, busy: wheelBusy, bpm: bpmInt)) {
            guard wheelOpen, !wheelBusy else { return }
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                wheelOpen = false
            }
        }
        .onChange(of: BeatPulseKey(isPlaying: ui.isPlaying, barIndex: ui.barIndex, beat: beat)) { _, key in
            guard key.isPlaying, key.beat >= 0 else {
                bpmScale = 1.0
                return
            }
            bpmScale = key.beat == 0 ? 1.06 : 1.03
            withAnimation(.spring(response: 0.24, dampingFraction: 0.65)) {
                bpmScale = 1.0
            }
        }
        .onDisappear {
            tapFlashTask?.cancel()
            tapFlashTask = nil
        }
        .sheet(isPresented: $meterSheet) {
            MeterPickerSheet(state: model.ui.state, model: model)
        }
        .sheet(isPresented: $presetsSheet) {
            PresetsSheet(ui: model.ui, model: model) {
                presetNameText = ""
                savePresetDialog = true
            }
        }
        .sheet(isPresented: $optionsSheet) {
            MetronomeOptionsSheet(ui: model.ui, model: model)
        }
        .alert("Tempo (BPM)", isPresented: $bpmDialog) {
            TextField("1–800", text: $bpmText)
                .keyboardType(.numberPad)
            Button("Cancel", role: .cancel) {}
            Button("Set") {
                if let parsed = Int(bpmText.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    model.setBpm(Double(parsed))
                }
            }
        } message: {
            Text("Enter a tempo between \(bpmMin) and \(bpmMax) BPM.")
        }
        .alert("Save Preset", isPresented: $savePresetDialog) {
            TextField("Preset name", text: $presetNameText)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                model.savePreset(name: presetNameText)
            }
        } message: {
            Text("Save current tempo, meter, subdivisions, and voices.")
        }
    }

    // MARK: - Portrait

    private func portraitBody(ui: MetronomeUiState) -> some View {
        VStack(spacing: 0) {
            headerRow(ui: ui, includeDots: false, includeSettings: false)
                .padding(.horizontal, 12)
                .padding(.top, 12)

            Spacer(minLength: 8)

            beatDots(ui: ui)
                .padding(.bottom, 6)

            tempoCluster(
                ui: ui,
                wheelHeight: 176,
                centerSize: 54,
                compactScale: 1.48,
                pitch: 28,
                isLandscape: false
            )

            Spacer(minLength: 8)

            tapPad(compact: false)

            Spacer(minLength: 14)

            Button {
                togglePlayback(ui: ui)
            } label: {
                Label(ui.isPlaying ? "Stop" : "Start", systemImage: ui.isPlaying ? "stop.fill" : "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 38)
            }
            .buttonStyle(.glassProminent)
            .tint(palette.accent)
            .controlSize(.large)
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
    }

    // MARK: - Landscape

    private func landscapeBody(ui: MetronomeUiState) -> some View {
        VStack(spacing: 8) {
            headerRow(ui: ui, includeDots: true, includeSettings: true)
                .padding(.horizontal, 12)
                .padding(.top, 12)

            HStack(spacing: 16) {
                VStack(spacing: 8) {
                    Spacer(minLength: 0)
                    tempoCluster(
                        ui: ui,
                        wheelHeight: 132,
                        centerSize: 40,
                        compactScale: 1.55,
                        pitch: 24,
                        isLandscape: true
                    )
                    Spacer(minLength: 0)
                    tapPad(compact: true)
                }
                .frame(maxWidth: .infinity)
                .padding(.leading, 14)
                .padding(.bottom, 14)

                VStack {
                    Spacer(minLength: 0)
                    Button {
                        togglePlayback(ui: ui)
                    } label: {
                        Label(ui.isPlaying ? "Stop" : "Start", systemImage: ui.isPlaying ? "stop.fill" : "play.fill")
                            .font(.title3.weight(.semibold))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(palette.accent)
                    .buttonBorderShape(.roundedRectangle(radius: 24))
                    .frame(minHeight: 80, maxHeight: 160)
                }
                .frame(maxWidth: .infinity)
                .padding(.trailing, 14)
                .padding(.bottom, 14)
            }
        }
    }

    // MARK: - Subviews

    private func headerRow(ui: MetronomeUiState, includeDots: Bool, includeSettings: Bool) -> some View {
        HStack(spacing: 6) {
            HeaderPicker(
                caption: "Meter",
                value: "\(ui.state.timeSignature.numerator)/\(ui.state.timeSignature.denominator)"
            ) {
                meterSheet = true
            }

            if includeDots {
                Spacer(minLength: 4)
                beatDots(ui: ui)
                Spacer(minLength: 4)
            } else {
                Spacer()
            }

            HeaderPicker(
                caption: "Preset",
                value: ui.presets.first(where: { $0.state == ui.state })?.name ?? "Custom"
            ) {
                presetsSheet = true
            }

            OptionsButton(label: "Metronome options") {
                optionsSheet = true
            }

            if includeSettings {
                LandscapeSettingsButton(action: onOpenSettings)
            }
        }
    }

    private func beatDots(ui: MetronomeUiState) -> some View {
        let modelInfo = meterModel(
            numerator: ui.state.timeSignature.numerator,
            denominator: ui.state.timeSignature.denominator
        )
        let beats = max(1, modelInfo.beatsPerBar)
        let currentBeat = currentBeat(for: ui)

        return HStack(spacing: beats > 6 ? 6 : 10) {
            ForEach(0..<beats, id: \.self) { beat in
                let isCurrent = ui.isPlaying && beat == currentBeat
                let isDownbeat = beat == 0
                let size: CGFloat = isDownbeat ? 14 : 10
                Circle()
                    .fill(
                        isCurrent
                            ? (ui.barActive ? palette.accent : palette.mutedLabel)
                            : palette.hairline
                    )
                    .frame(width: size, height: size)
                    .scaleEffect(isCurrent ? 1.25 : 1.0)
                    .animation(.spring(response: 0.18, dampingFraction: 0.7), value: currentBeat)
            }
        }
        .frame(height: 22)
    }

    private func tempoCluster(
        ui: MetronomeUiState,
        wheelHeight: CGFloat,
        centerSize: CGFloat,
        compactScale: CGFloat,
        pitch: CGFloat,
        isLandscape: Bool
    ) -> some View {
        let bpmInt = Int(ui.state.bpm.rounded())
        let collapse: CGFloat = wheelOpen ? 0.0 : 1.0
        let openBand = centerSize + 18
        let foldedBand = centerSize * compactScale * 1.12
        let bandHeight = openBand * (1.0 - collapse) + foldedBand * collapse
        let statusText = ui.inCountIn ? "Count-in" : (!ui.barActive && ui.isPlaying ? "Muted bar" : "")

        return VStack(spacing: isLandscape ? 4 : 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(palette.paper)
                    .frame(height: bandHeight)

                HStack(spacing: 8) {
                    Button {
                        model.changeBpm(-1)
                    } label: {
                        Image(systemName: "minus")
                            .font(.title3.weight(.semibold))
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .controlSize(.large)
                    .buttonRepeatBehavior(.enabled)
                    .disabled(bpmInt <= bpmMin)
                    .accessibilityLabel("Decrease BPM")

                    BpmWheel(
                        value: bpmInt,
                        range: bpmMin...bpmMax,
                        centerSize: centerSize,
                        pitch: pitch,
                        compactScale: compactScale,
                        collapse: collapse,
                        centerScale: bpmScale,
                        centerColor: palette.accent,
                        onValueChange: { next in
                            model.setBpm(Double(next))
                        },
                        onCenterTap: {
                            bpmText = "\(bpmInt)"
                            bpmDialog = true
                        },
                        onInteractionChange: { active in
                            if active {
                                withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                                    wheelOpen = true
                                }
                            }
                            wheelBusy = active
                        }
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: wheelHeight)

                    Button {
                        model.changeBpm(1)
                    } label: {
                        Image(systemName: "plus")
                            .font(.title3.weight(.semibold))
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .controlSize(.large)
                    .buttonRepeatBehavior(.enabled)
                    .disabled(bpmInt >= bpmMax)
                    .accessibilityLabel("Increase BPM")
                }
                .padding(.horizontal, 12)
            }
            .padding(.horizontal, isLandscape ? 4 : 16)

            Button {
                bpmText = "\(bpmInt)"
                bpmDialog = true
            } label: {
                if isLandscape {
                    HStack(spacing: 6) {
                        Text("BPM · \(getTempoMarking(bpm: ui.state.bpm))")
                            .foregroundStyle(palette.mutedLabel)
                        if !statusText.isEmpty {
                            Text(statusText)
                                .fontWeight(.semibold)
                                .foregroundStyle(ui.inCountIn ? palette.accent : palette.mutedLabel)
                        }
                    }
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                    .contentShape(Rectangle())
                } else {
                    VStack(spacing: 3) {
                        Text("BPM · \(getTempoMarking(bpm: ui.state.bpm))")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(palette.mutedLabel)
                        Text(statusText.isEmpty ? " " : statusText)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ui.inCountIn ? palette.accent : palette.mutedLabel)
                            .opacity(statusText.isEmpty ? 0 : 1)
                    }
                    .contentShape(Rectangle())
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Tempo marking \(getTempoMarking(bpm: ui.state.bpm)), tap to enter BPM")
        }
    }

    @ViewBuilder
    private func tapPad(compact: Bool) -> some View {
        if compact {
            Button(action: handleTap) {
                Text("Tap")
                    .font(.headline)
                    .foregroundStyle(tapFlash ? palette.accent : palette.ink)
                    .frame(maxWidth: .infinity, minHeight: 34)
            }
            .buttonStyle(.glass)
            .controlSize(.large)
        } else {
            Button(action: handleTap) {
                Text("Tap")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(tapFlash ? palette.accent : palette.ink)
                    .frame(width: 96, height: 96)
                    .background(
                        Circle()
                            .fill(palette.accent.opacity(tapFlash ? 0.14 : 0.0))
                    )
                    .overlay(
                        Circle()
                            .strokeBorder(palette.accent.opacity(tapFlash ? 0.55 : 0.0), lineWidth: 2)
                    )
                    .contentShape(Circle())
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
        }
    }

    // MARK: - Actions & Helpers

    private func togglePlayback(ui: MetronomeUiState) {
        if !ui.isPlaying {
            withAnimation(.spring(response: 0.30, dampingFraction: 0.86)) {
                wheelOpen = false
            }
        }
        model.toggle()
    }

    private func handleTap() {
        model.registerTap()
        #if canImport(UIKit)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        #endif
        tapFlashTask?.cancel()
        tapFlash = true
        tapFlashTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(160))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.22)) {
                tapFlash = false
            }
        }
    }

    private func currentBeat(for ui: MetronomeUiState) -> Int {
        guard ui.isPlaying else { return -1 }
        let modelInfo = meterModel(
            numerator: ui.state.timeSignature.numerator,
            denominator: ui.state.timeSignature.denominator
        )
        let beats = max(1, modelInfo.beatsPerBar)
        return min(beats - 1, Int(ui.progress * Double(beats)))
    }
}

private struct WheelFoldKey: Equatable {
    let open: Bool
    let busy: Bool
    let bpm: Int
}

private struct BeatPulseKey: Equatable {
    let isPlaying: Bool
    let barIndex: Int
    let beat: Int
}

// MARK: - Sheets

private struct MeterPickerSheet: View {
    let state: MetronomeState
    let model: MetronomeModelView

    var body: some View {
        let info = meterModel(numerator: state.timeSignature.numerator, denominator: state.timeSignature.denominator)
        NativeOptionSheet(title: "Time Signature", detents: [.height(280), .medium]) {
            Section {
                Stepper(
                    value: Binding(
                        get: { state.timeSignature.numerator },
                        set: { model.setTimeSignature(numerator: $0, denominator: state.timeSignature.denominator) }
                    ),
                    in: 1...32
                ) {
                    LabeledContent("Beats", value: "\(state.timeSignature.numerator)")
                }

                HStack {
                    Text("Note value")
                    Spacer()
                    Picker("Note value", selection: Binding(
                        get: { state.timeSignature.denominator },
                        set: { model.setTimeSignature(numerator: state.timeSignature.numerator, denominator: $0) }
                    )) {
                        ForEach(denominators, id: \.self) { d in
                            Text("/\(d)").tag(d)
                        }
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 190)
                }
            } footer: {
                Text(describeMeter(info))
            }
        }
    }
}

private struct PresetsSheet: View {
    @Environment(\.dismiss) private var dismiss
    let ui: MetronomeUiState
    let model: MetronomeModelView
    let onRequestSave: () -> Void

    var body: some View {
        NativeOptionSheet(title: "Presets") {
            Section {
                Button {
                    dismiss()
                    onRequestSave()
                } label: {
                    Label("Save current setup", systemImage: "plus.circle.fill")
                }
            }

            Section("Saved presets") {
                if ui.presets.isEmpty {
                    Text("No saved presets yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(ui.presets) { preset in
                        NativeOptionRow(
                            title: preset.name,
                            subtitle: "\(Int(preset.state.bpm.rounded())) BPM · \(preset.state.timeSignature.numerator)/\(preset.state.timeSignature.denominator)",
                            selected: preset.state == ui.state
                        ) {
                            model.applyPreset(id: preset.id)
                            dismiss()
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                model.deletePreset(id: preset.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                        .contextMenu {
                            Button(role: .destructive) {
                                model.deletePreset(id: preset.id)
                            } label: {
                                Label("Delete preset", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct MetronomeOptionsSheet: View {
    @Environment(\.omniPalette) private var palette
    let ui: MetronomeUiState
    let model: MetronomeModelView

    var body: some View {
        let state = ui.state
        NativeFormSheet(title: "Metronome Options") {
            Section("Subdivision") {
                Stepper(
                    value: Binding(
                        get: { state.divisionsPerBeat },
                        set: { model.setDivisionsPerBeat($0) }
                    ),
                    in: divisionsMin...divisionsMax
                ) {
                    LabeledContent("Pulses per beat", value: "\(state.divisionsPerBeat)")
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(subdivisions) { opt in
                            let active = state.divisionsPerBeat == opt.n
                            if active {
                                Button(opt.shortLabel) {
                                    model.setDivisionsPerBeat(opt.n)
                                }
                                .buttonStyle(.glassProminent)
                                .tint(palette.accent)
                                .controlSize(.small)
                            } else {
                                Button(opt.shortLabel) {
                                    model.setDivisionsPerBeat(opt.n)
                                }
                                .buttonStyle(.glass)
                                .controlSize(.small)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }

            Section("Bar mute pattern") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(patternPresets, id: \.label) { preset in
                            let active = state.barPattern == preset.bars
                            if active {
                                Button(preset.label) {
                                    model.setBarPattern(preset.bars)
                                }
                                .buttonStyle(.glassProminent)
                                .tint(palette.accent)
                                .controlSize(.small)
                            } else {
                                Button(preset.label) {
                                    model.setBarPattern(preset.bars)
                                }
                                .buttonStyle(.glass)
                                .controlSize(.small)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }

                Stepper(
                    value: Binding(
                        get: { state.barPattern.count },
                        set: { next in
                            var pat = state.barPattern
                            if pat.count < next {
                                pat.append(contentsOf: repeatElement(1, count: next - pat.count))
                            } else if pat.count > next {
                                pat = Array(pat.prefix(next))
                            }
                            model.setBarPattern(pat)
                        }
                    ),
                    in: patternMinBars...patternMaxBars
                ) {
                    LabeledContent("Bars in cycle", value: "\(state.barPattern.count)")
                }

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 42), spacing: 8)], spacing: 8) {
                    ForEach(state.barPattern.indices, id: \.self) { idx in
                        let on = state.barPattern[idx] == 1
                        let isPlayingBar = ui.isPlaying && !ui.inCountIn && ui.patternPos == idx
                        Button {
                            var pat = state.barPattern
                            pat[idx] = on ? 0 : 1
                            model.setBarPattern(pat)
                        } label: {
                            Text("\(idx + 1)")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(on ? palette.onAccent : .secondary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 36)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(on ? palette.accent : Color.secondary.opacity(0.15))
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .stroke(isPlayingBar ? Color.primary : Color.clear, lineWidth: 2)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }

            Section("Polyrhythm") {
                Toggle("Enable polyrhythm", isOn: Binding(
                    get: { state.poly.enabled },
                    set: { model.setPolyEnabled($0) }
                ))

                if state.poly.enabled {
                    Stepper(
                        value: Binding(
                            get: { state.poly.events },
                            set: { model.changePolyEvents($0 - state.poly.events) }
                        ),
                        in: polyMin...polyMax
                    ) {
                        LabeledContent("Cross-beats per bar", value: "\(state.poly.events)")
                    }

                    Toggle("Accent first pulse", isOn: Binding(
                        get: { state.poly.accentFirst },
                        set: { model.setPolyAccentFirst($0) }
                    ))
                }
            }

            Section("Timing & ramp") {
                Toggle("Count-in bar", isOn: Binding(
                    get: { state.countIn },
                    set: { model.setCountIn($0) }
                ))

                Toggle("Tempo ramp", isOn: Binding(
                    get: { state.ramp.enabled },
                    set: { model.setRampEnabled($0) }
                ))

                if state.ramp.enabled {
                    Stepper(
                        value: Binding(
                            get: { Int(state.ramp.targetBpm.rounded()) },
                            set: { model.setRampTargetBpm($0) }
                        ),
                        in: bpmMin...bpmMax
                    ) {
                        LabeledContent("Target BPM", value: "\(Int(state.ramp.targetBpm.rounded()))")
                    }

                    Stepper(
                        value: Binding(
                            get: { state.ramp.bars },
                            set: { model.setRampBars($0) }
                        ),
                        in: rampBarsMin...rampBarsMax
                    ) {
                        LabeledContent("Over bars", value: "\(state.ramp.bars)")
                    }
                }
            }

            Section("Sounds & volume") {
                VStack(alignment: .leading, spacing: 4) {
                    LabeledContent("Master volume", value: "\(Int((state.masterVol * 100).rounded()))%")
                    Slider(value: Binding(
                        get: { state.masterVol },
                        set: { model.setMasterVolume($0) }
                    ), in: 0...1)
                }

                ForEach(SoundSlot.allCases) { slot in
                    let role = state.sounds.role(for: slot)
                    VStack(spacing: 6) {
                        Picker(slot.rawValue, selection: Binding(
                            get: { role.id },
                            set: { next in
                                model.setVoice(slot: slot, voiceId: next)
                                model.previewVoice(id: next, vol: role.vol * state.masterVol)
                            }
                        )) {
                            ForEach(MetronomeVoices.options) { opt in
                                Text(opt.label).tag(opt.id)
                            }
                        }
                        .pickerStyle(.menu)
                        Slider(value: Binding(
                            get: { role.vol },
                            set: { model.setSlotVolume(slot: slot, volume: $0) }
                        ), in: 0...1)
                    }
                }
            }
        }
    }
}
