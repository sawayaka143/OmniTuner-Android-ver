import SwiftUI

public struct ReferencePitchView: View {
    @Environment(\.omniPalette) private var palette
    @Bindable var container: AppContainer

    public init(container: AppContainer) {
        self.container = container
    }

    public var body: some View {
        let hz = container.tuner.value.referencePitch
        Form {
            Section {
                Picker("Frequency", selection: Binding(
                    get: { container.tuner.value.referencePitch },
                    set: { next in container.tuner.update { $0.referencePitch = next } }
                )) {
                    ForEach(referencePitchRange, id: \.self) { value in
                        Text("\(value) Hz")
                            .font(.title3.monospacedDigit())
                            .tag(value)
                    }
                }
                .pickerStyle(.wheel)
                .frame(height: 180)
            } header: {
                Text("A4 Reference")
            } footer: {
                Text("Standard concert pitch is 440 Hz.")
            }

            if hz != defaultReferencePitch {
                Section {
                    Button("Reset to 440 Hz") {
                        container.tuner.update { $0.referencePitch = defaultReferencePitch }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    .foregroundStyle(palette.accent)
                }
            }
        }
        .navigationTitle("Reference pitch")
        .navigationBarTitleDisplayMode(.inline)
    }
}
