import OmniTunerCore
import SwiftUI

// MARK: - Pedalboard Containers

public struct PedalPanel<Content: View>: View {
    @Environment(\.omniPalette) private var palette
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        content
            .background(
                RoundedRectangle(cornerRadius: 32, style: .continuous)
                    .fill(palette.boardGrey)
            )
            .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
    }
}

public struct PedalCard<Content: View>: View {
    @Environment(\.omniPalette) private var palette
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        content
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(palette.boardGrey)
            )
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

// MARK: - Headers & Actions

public struct HeaderPicker: View {
    @Environment(\.omniPalette) private var palette
    public let caption: String
    public let value: String
    public let action: () -> Void

    public init(caption: String, value: String, action: @escaping () -> Void) {
        self.caption = caption
        self.value = value
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 1) {
                Text(caption)
                    .font(.caption)
                    .foregroundStyle(palette.mutedLabel)
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Text(value)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(palette.ink)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(palette.mutedLabel)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

public struct TuningHeaderPicker: View {
    public let instrument: Instrument
    public let tuning: Tuning
    public let action: () -> Void

    public init(instrument: Instrument, tuning: Tuning, action: @escaping () -> Void) {
        self.instrument = instrument
        self.tuning = tuning
        self.action = action
    }

    public var body: some View {
        HeaderPicker(
            caption: instrument.label,
            value: tuning.label,
            action: action
        )
    }
}

public struct OptionsButton: View {
    public let action: () -> Void
    public let label: String

    public init(label: String = "Options", action: @escaping () -> Void) {
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Image(systemName: "slider.horizontal.3")
                .font(.system(size: 17, weight: .medium))
                .frame(width: 22, height: 22)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .accessibilityLabel(label)
    }
}

public struct LandscapeSettingsButton: View {
    public let action: () -> Void

    public init(action: @escaping () -> Void) {
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Image(systemName: "gearshape.fill")
                .font(.system(size: 17, weight: .medium))
                .frame(width: 22, height: 22)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .accessibilityLabel("Settings")
    }
}
