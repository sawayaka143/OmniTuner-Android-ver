import SwiftUI

/// Pick-one grouped sheet matching Android's OptionSheet pattern using native iOS inset-grouped List,
/// drag indicator, detents, and Liquid Glass close button.
public struct NativeOptionSheet<Content: View>: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let title: String
    public let detents: Set<PresentationDetent>
    private let content: Content

    public init(
        title: String,
        detents: Set<PresentationDetent> = [.medium, .large],
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.detents = detents
        self.content = content()
    }

    public var body: some View {
        NavigationStack {
            List {
                content
            }
            .listStyle(.insetGrouped)
            .navigationTitle(title)
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
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
    }
}

/// Mixed-control sheet that stays open while adjusting toggles, steppers, sliders, and segmented pickers.
public struct NativeFormSheet<Content: View>: View {
    @Environment(\.omniPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    public let title: String
    public let detents: Set<PresentationDetent>
    private let content: Content

    public init(
        title: String,
        detents: Set<PresentationDetent> = [.medium, .large],
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.detents = detents
        self.content = content()
    }

    public var body: some View {
        NavigationStack {
            Form {
                content
            }
            .navigationTitle(title)
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
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
    }
}

/// Standard selectable row for `NativeOptionSheet` with optional subtitle and checkmark.
public struct NativeOptionRow: View {
    @Environment(\.omniPalette) private var palette

    public let title: String
    public let subtitle: String?
    public let selected: Bool
    public let onSelect: () -> Void

    public init(
        title: String,
        subtitle: String? = nil,
        selected: Bool,
        onSelect: @escaping () -> Void
    ) {
        self.title = title
        self.subtitle = subtitle
        self.selected = selected
        self.onSelect = onSelect
    }

    public var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body.weight(selected ? .semibold : .regular))
                        .foregroundStyle(selected ? palette.accent : Color.primary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.accent)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
