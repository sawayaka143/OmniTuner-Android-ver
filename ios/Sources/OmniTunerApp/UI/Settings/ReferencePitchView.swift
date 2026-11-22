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
          Group {
            Section {
                Picker(selection: Binding(
                    get: { container.tuner.value.referencePitch },
                    set: { next in container.tuner.update { $0.referencePitch = next } }
                )) {
                    ForEach(referencePitchRange, id: \.self) { value in
                        Text("\(value) Hz", bundle: .module)
                            .font(.title3.monospacedDigit())
                            .tag(value)
                    }
                } label: {
                    Text("Frequency", bundle: .module)
                }
                .pickerStyle(.wheel)
                .frame(height: 180)
            } footer: {
                Text("Standard concert pitch is 440 Hz.", bundle: .module)
            }

            if hz != defaultReferencePitch {
                Section {
                    Button {
                        container.tuner.update { $0.referencePitch = defaultReferencePitch }
                    } label: {
                        Text("Reset to 440 Hz", bundle: .module)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    .foregroundStyle(palette.accent)
                }
            }
          }
          .settingsRows()
        }
        .settingsPageBackground()
        .navigationTitle(Text("Reference pitch", bundle: .module))
        .navigationBarTitleDisplayMode(.inline)
    }
}
