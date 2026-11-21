import SwiftUI

public struct NeedleMeterView: View {
    @Environment(\.omniPalette) private var palette

    public let needlePercent: Double
    public let needleColor: Color
    public let cents: Double?
    public let dimmed: Bool
    public let inputLevel: Float

    nonisolated private static let rangeCents = 50
    nonisolated private static let tickStepCents = 5
    nonisolated private static let majorTicks: Set<Int> = [0, 10, 25, 50]
    nonisolated private static let labelledTicks = [-50, -25, 0, 25, 50]

    public init(
        needlePercent: Double,
        needleColor: Color,
        cents: Double?,
        dimmed: Bool = false,
        inputLevel: Float = 0
    ) {
        self.needlePercent = needlePercent
        self.needleColor = needleColor
        self.cents = cents
        self.dimmed = dimmed
        self.inputLevel = inputLevel
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
        Canvas { ctx, size in
            let centerY = size.height * 0.42
            let majorHeight = size.height * 0.30
            let minorHeight = size.height * 0.18
            let spanInset: CGFloat = 14
            let spanWidth = max(1, size.width - 2 * spanInset)

            func xAt(_ c: Double) -> CGFloat {
                spanInset + spanWidth * CGFloat((c + Double(Self.rangeCents)) / Double(2 * Self.rangeCents))
            }

            // Soft in-tune zone band behind the center ticks
            let bandLeft = xAt(-Double(inTuneToleranceCents))
            let bandRight = xAt(Double(inTuneToleranceCents))
            let bandHeight = majorHeight * 1.7
            let bandRect = CGRect(x: bandLeft, y: centerY - bandHeight / 2, width: bandRight - bandLeft, height: bandHeight)
            ctx.fill(
                Path(roundedRect: bandRect, cornerRadius: (bandRight - bandLeft) / 2, style: .continuous),
                with: .color(needleColor.opacity(0.14))
            )

            for c in stride(from: -Self.rangeCents, through: Self.rangeCents, by: Self.tickStepCents) {
                let major = Self.majorTicks.contains(abs(c))
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

            let needleX = spanInset + spanWidth * CGFloat(min(1.0, max(0.0, needlePercent / 100.0)))
            let needleHalf = majorHeight * 1.1
            let level = CGFloat(min(1.0, max(0.0, inputLevel)))
            let restHalf = majorHeight * (0.5 + 0.6 * level)

            var restPath = Path()
            restPath.move(to: CGPoint(x: xAt(0), y: centerY - restHalf))
            restPath.addLine(to: CGPoint(x: xAt(0), y: centerY + restHalf))
            ctx.stroke(
                restPath,
                with: .color(needleColor.opacity((0.35 + 0.35 * Double(level)) * (1.0 - needleOpacity))),
                style: StrokeStyle(lineWidth: 2.2, lineCap: .round)
            )

            var needlePath = Path()
            needlePath.move(to: CGPoint(x: needleX, y: centerY - needleHalf))
            needlePath.addLine(to: CGPoint(x: needleX, y: centerY + needleHalf))
            ctx.stroke(
                needlePath,
                with: .color(needleColor.opacity(needleOpacity)),
                style: StrokeStyle(lineWidth: 3.4, lineCap: .round)
            )

            let labelTop = centerY + needleHalf + 12
            for c in Self.labelledTicks {
                let text = c > 0 ? "+\(c)" : "\(c)"
                let resolved = ctx.resolve(
                    Text(text)
                        .font(.system(size: 11, weight: c == 0 ? .bold : .regular).monospacedDigit())
                        .foregroundStyle(c == 0 ? needleColor : palette.mutedLabel)
                )
                ctx.draw(resolved, at: CGPoint(x: xAt(Double(c)), y: labelTop), anchor: .center)
            }
        }
        .frame(height: 120)
        .animation(.easeOut(duration: 0.12), value: needlePercent)
        .animation(.easeOut(duration: 0.2), value: needleOpacity)
        .accessibilityLabel(cents == nil ? "Tuning meter, no pitch detected" : "Tuning meter, \(Int((cents ?? 0).rounded())) cents")
    }
}
