import SwiftUI

public struct LicensesView: View {
    private static let apacheURL = URL(string: "https://www.apache.org/licenses/LICENSE-2.0")!

    private struct LibraryEntry: Identifiable {
        let id = UUID()
        let name: String
        let author: String
    }

    private let libraries: [LibraryEntry] = [
        LibraryEntry(name: "OmniTuner Core", author: "OmniTuner (ported from shared KMP)"),
        LibraryEntry(name: "Swift Standard Library", author: "Apple Inc."),
        LibraryEntry(name: "SwiftUI & AVFoundation", author: "Apple Inc."),
    ]

    public init() {}

    public var body: some View {
        List {
          Group {
            Section {
                ForEach(libraries) { lib in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(lib.name)
                            .font(.body)
                        Text("\(lib.author) · Apache License 2.0")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text("Libraries", bundle: .module)
            }

            Section {
                Link(destination: Self.apacheURL) {
                    HStack {
                        Text("Apache License 2.0")
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text("License", bundle: .module)
            }
          }
          .settingsRows()
        }
        .listStyle(.insetGrouped)
        .settingsPageBackground()
        .navigationTitle(Text("Open-source licenses", bundle: .module))
        .navigationBarTitleDisplayMode(.inline)
    }
}
