import SwiftUI

public struct NeedleMeterView: View {
    public let needlePercent: Double
    public let needleColor: Color
    public let cents: Double?
    public let dimmed: Bool
    public let showTickLabels: Bool
    public let model: TunerModel

    nonisolated private static let rangeCents = 50
    nonisolated private static let tickStepCents = 5
    nonisolated private static let majorTicks: Set<Int> = [0, 10, 25, 50]
    nonisolated private static let labelledTicks = [-50, -25, 0, 25, 50]
    nonisolated private static let spanInset: CGFloat = 14

    public init(
        needlePercent: Double,
        needleColor: Color,
        cents: Double?,
        dimmed: Bool = false,
        showTickLabels: Bool = true,
        model: TunerModel
    ) {
        self.needlePercent = needlePercent
        self.needleColor = needleColor
        self.cents = cents
        self.dimmed = dimmed
        self.showTickLabels = showTickLabels
        self.model = model
    }

    private var offScale: Bool {
        guard let cents else { return false }
        return abs(cents) > Double(Self.rangeCents)
    }

    private var needleOpacity: Double {
        if cents == nil { return 0 }
        if offScale || dimmed { return 0.5 }
        return 1.0
    }

    public var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let centerX = size.width * 0.5
            let centerY = size.height * 0.42
            let majorHeight = size.height * 0.30
            let spanWidth = max(1, size.width - 2 * Self.spanInset)
            let needleHalf = majorHeight * 1.1
            let clampedFraction = min(1.0, max(0.0, needlePercent / 100.0))
            let needleOffsetX = (clampedFraction - 0.5) * spanWidth

            ZStack {
                MeterScaleCanvas(
                    rangeCents: Self.rangeCents,
                    tickStepCents: Self.tickStepCents,
                    majorTicks: Self.majorTicks,
                    labelledTicks: Self.labelledTicks,
                    showTickLabels: showTickLabels,
                    spanInset: Self.spanInset
                )

                MeterRestingMark(
                    model: model,
                    needleColor: needleColor,
                    centerX: centerX,
                    centerY: centerY,
                    majorHeight: majorHeight,
                    needleOpacity: needleOpacity
                )

                Capsule()
                    .fill(needleColor)
                    .frame(width: 3.4, height: needleHalf * 2)
                    .position(x: centerX, y: centerY)
                    .offset(x: needleOffsetX)
                    .opacity(needleOpacity)
                    .animation(.spring(response: 0.18, dampingFraction: 0.78), value: needleOffsetX)
                    .animation(.easeOut(duration: 0.2), value: needleOpacity)
                    .animation(.easeOut(duration: 0.12), value: needleColor)
            }
        }
        .frame(height: 120)
        .accessibilityLabel(cents == nil ? "Tuning meter, no pitch detected" : "Tuning meter, \(Int((cents ?? 0).rounded())) cents")
    }
}

private struct MeterScaleCanvas: View {
    @Environment(\.omniPalette) private var palette

    let rangeCents: Int
    let tickStepCents: Int
    let majorTicks: Set<Int>
    let labelledTicks: [Int]
    let showTickLabels: Bool
    let spanInset: CGFloat

    var body: some View {
        Canvas { ctx, size in
            let centerY = size.height * 0.42
            let majorHeight = size.height * 0.30
            let minorHeight = size.height * 0.18
            let spanWidth = max(1, size.width - 2 * spanInset)

            func xAt(_ c: Double) -> CGFloat {
                spanInset + spanWidth * CGFloat((c + Double(rangeCents)) / Double(2 * rangeCents))
            }

            // Soft in-tune zone band behind the center ticks (stays accent-colored)
            let bandLeft = xAt(-Double(inTuneToleranceCents))
            let bandRight = xAt(Double(inTuneToleranceCents))
            let bandHeight = majorHeight * 1.7
            let bandRect = CGRect(
                x: bandLeft,
                y: centerY - bandHeight / 2,
                width: bandRight - bandLeft,
                height: bandHeight
            )
            ctx.fill(
                Path(roundedRect: bandRect, cornerRadius: (bandRight - bandLeft) / 2, style: .continuous),
                with: .color(palette.accent.opacity(0.14))
            )

            for c in stride(from: -rangeCents, through: rangeCents, by: tickStepCents) {
                let major = majorTicks.contains(abs(c))
                let tickH = major ? majorHeight : minorHeight
                let x = xAt(Double(c))
                var p = Path()
                p.move(to: CGPoint(x: x, y: centerY - tickH / 2))
                p.addLine(to: CGPoint(x: x, y: centerY + tickH / 2))
                ctx.stroke(
                    p,
                    with: .color(palette.mutedLabel.opacity(major ? 0.75 : 0.45)),
                    style: StrokeStyle(lineWidth: major ? 1.8 : 1.1, lineCap: .round)
                )
            }

            if showTickLabels {
                let needleHalf = majorHeight * 1.1
                let labelTop = centerY + needleHalf + 12
                for c in labelledTicks {
                    let text = c > 0 ? "+\(c)" : "\(c)"
                    let resolved = ctx.resolve(
                        Text(text)
                            .font(.system(size: 11, weight: c == 0 ? .bold : .regular).monospacedDigit())
                            .foregroundStyle(c == 0 ? palette.accent : palette.mutedLabel)
                    )
                    ctx.draw(resolved, at: CGPoint(x: xAt(Double(c)), y: labelTop), anchor: .center)
                }
            }
        }
    }
}

private struct MeterRestingMark: View {
    let model: TunerModel
    let needleColor: Color
    let centerX: CGFloat
    let centerY: CGFloat
    let majorHeight: CGFloat
    let needleOpacity: Double

    var body: some View {
        let level = CGFloat(min(1.0, max(0.0, model.inputLevel)))
        let maxHeight = majorHeight * 2.2
        let scaleY = (0.5 + 0.6 * level) / 1.1
        let opacity = (0.35 + 0.35 * Double(level)) * (1.0 - needleOpacity)

        Capsule()
            .fill(needleColor)
            .frame(width: 2.2, height: maxHeight)
            .scaleEffect(y: scaleY, anchor: .center)
            .position(x: centerX, y: centerY)
            .opacity(opacity)
            .animation(.linear(duration: 0.06), value: level)
            .animation(.easeOut(duration: 0.2), value: needleOpacity)
    }
}
