import OmniTunerCore
import SwiftUI

public struct NeckDiagramView: View {
    @Environment(\.omniPalette) private var palette

    public let frets: [Int?]
    public let tuning: ParsedTuning
    public let symbol: String
    public let rootPc: Int
    public let index: Int
    public let labels: DiagramLabels
    public let flats: Bool
    public let stretch: Bool

    nonisolated private static let row: CGFloat = 24
    nonisolated private static let col: CGFloat = 30
    nonisolated private static let left: CGFloat = 22
    nonisolated private static let ind: CGFloat = 26
    nonisolated private static let pad: CGFloat = 8
    nonisolated private static let breakGap: CGFloat = 10
    nonisolated private static let top: CGFloat = 6
    nonisolated private static let bot: CGFloat = 18
    nonisolated private static let ringRadius: CGFloat = 10
    nonisolated private static let minColumns = 5
    nonisolated private static let maxColStretch: CGFloat = 1.8
    nonisolated private static let maxRowStretch: CGFloat = 1.6
    nonisolated private static let lastFret = 24
    nonisolated private static let singleInlays: Set<Int> = [3, 5, 7, 9, 15, 17, 19, 21]
    nonisolated private static let doubleInlays: Set<Int> = [12, 24]

    public init(
        frets: [Int?],
        tuning: ParsedTuning,
        symbol: String,
        rootPc: Int,
        index: Int,
        labels: DiagramLabels,
        flats: Bool,
        stretch: Bool = false
    ) {
        self.frets = frets
        self.tuning = tuning
        self.symbol = symbol
        self.rootPc = rootPc
        self.index = index
        self.labels = labels
        self.flats = flats
        self.stretch = stretch
    }

    private func ringColor(degree: Int) -> Color {
        switch degree {
        case 0: return palette.accent
        case 3, 4: return palette.goodGreen
        case 5, 7: return palette.mutedLabel
        case 6, 8, 10, 11: return palette.warnAmber
        default: return palette.infoViolet
        }
    }

    public var body: some View {
        let n = tuning.midi.count
        let fretted = frets.compactMap { $0 }.filter { $0 > 0 }
        let minFret = fretted.min() ?? 1
        let maxFretVal = fretted.max() ?? 1
        let upperBound = max(Self.lastFret, maxFretVal)
        let rawLo = (minFret - 1 <= 2) ? 1 : (minFret - 1)
        let hi = min(upperBound, max(maxFretVal + 1, rawLo + Self.minColumns - 1))
        let lo = hi == upperBound ? max(1, min(rawLo, hi - Self.minColumns + 1)) : rawLo
        let cols = hi - lo + 1
        let broken = lo > 1 && frets.contains { $0 == 0 }
        let gridL = Self.left + Self.ind + (broken ? Self.breakGap : 0)
        let naturalWidth = gridL + CGFloat(cols) * Self.col + Self.pad
        let naturalHeight = Self.top + CGFloat(n) * Self.row + Self.bot

        Canvas { ctx, size in
            let unit = stretch ? min(size.width / naturalWidth, size.height / naturalHeight) : (size.width / naturalWidth)
            let colW: CGFloat = stretch
                ? min(Self.col * Self.maxColStretch, max(Self.col, (size.width / unit - gridL - Self.pad) / CGFloat(cols)))
                : Self.col
            // When the width is the limit (few strings, a narrow card), spread the strings
            // to fill the leftover height, as colW does for the frets.
            let rowH: CGFloat = stretch
                ? min(Self.row * Self.maxRowStretch, max(Self.row, (size.height / unit - Self.top - Self.bot) / CGFloat(n)))
                : Self.row
            let gridR = gridL + CGFloat(cols) * colW
            let dx = (size.width - (gridR + Self.pad) * unit) / 2

            func u(_ v: CGFloat) -> CGFloat { v * unit }
            func fretX(_ f: Int) -> CGFloat { gridL + CGFloat(f - lo) * colW + colW / 2 }
            func stringY(_ s: Int) -> CGFloat { Self.top + CGFloat(n - 1 - s) * rowH + rowH / 2 }
            let yTop = Self.top
            let yBot = Self.top + CGFloat(n) * rowH
            let indCx = Self.left + Self.ind / 2

            ctx.translateBy(x: dx, y: 0)

            for f in lo..<(lo + cols) {
                let cx = u(fretX(f))
                if Self.singleInlays.contains(f) {
                    let r = u(4.5)
                    ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: u((yTop + yBot) / 2) - r, width: r * 2, height: r * 2)), with: .color(palette.hairline))
                } else if Self.doubleInlays.contains(f) {
                    let r = u(4.5)
                    for ratio in [0.3, 0.7] {
                        let cy = u(yTop + ratio * (yBot - yTop))
                        ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(palette.hairline))
                    }
                }
            }

            for k in 0...cols {
                let x = u(gridL + CGFloat(k) * colW)
                let isNut = k == 0 && lo == 1
                let color: Color = isNut ? palette.mutedLabel : (k == 0 ? palette.mutedLabel.opacity(0.6) : palette.hairline)
                var path = Path()
                path.move(to: CGPoint(x: x, y: u(yTop - 2)))
                path.addLine(to: CGPoint(x: x, y: u(yBot + 2)))
                ctx.stroke(path, with: .color(color), lineWidth: u(isNut ? 4 : 1))
            }

            if broken {
                let x = u(gridL - 3)
                var path = Path()
                path.move(to: CGPoint(x: x, y: u(yTop - 2)))
                path.addLine(to: CGPoint(x: x, y: u(yBot + 2)))
                ctx.stroke(path, with: .color(palette.mutedLabel.opacity(0.6)), lineWidth: u(1))
            }

            for s in 0..<n {
                let y = u(stringY(s))
                if broken {
                    var leftP = Path()
                    leftP.move(to: CGPoint(x: u(Self.left), y: y))
                    leftP.addLine(to: CGPoint(x: u(Self.left + Self.ind), y: y))
                    ctx.stroke(leftP, with: .color(palette.mutedLabel.opacity(0.6)), lineWidth: u(1))

                    var rightP = Path()
                    rightP.move(to: CGPoint(x: u(gridL), y: y))
                    rightP.addLine(to: CGPoint(x: u(gridR), y: y))
                    ctx.stroke(rightP, with: .color(palette.mutedLabel.opacity(0.6)), lineWidth: u(1))
                } else {
                    var p = Path()
                    p.move(to: CGPoint(x: u(Self.left), y: y))
                    p.addLine(to: CGPoint(x: u(gridR), y: y))
                    ctx.stroke(p, with: .color(palette.mutedLabel.opacity(0.6)), lineWidth: u(1))
                }
                let muted = frets[s] == nil
                let labelColor = muted ? palette.mutedLabel.opacity(0.5) : palette.mutedLabel
                let name = pcName(mod12(tuning.midi[s]), flats: tuning.flats)
                let resolved = ctx.resolve(Text(name).font(.system(size: u(9))).foregroundStyle(labelColor))
                ctx.draw(resolved, at: CGPoint(x: u(Self.left - 4), y: y), anchor: .trailing)
            }

            let axisY = u(yBot + Self.bot / 2 + 1)
            if frets.contains(0) {
                let res = ctx.resolve(Text("0").font(.system(size: u(9))).foregroundStyle(palette.mutedLabel.opacity(0.6)))
                ctx.draw(res, at: CGPoint(x: u(indCx), y: axisY), anchor: .center)
            }
            for f in lo..<(lo + cols) {
                let res = ctx.resolve(Text("\(f)").font(.system(size: u(9))).foregroundStyle(palette.mutedLabel.opacity(0.6)))
                ctx.draw(res, at: CGPoint(x: u(fretX(f)), y: axisY), anchor: .center)
            }

            for s in 0..<n {
                let y = stringY(s)
                guard let fret = frets[s] else {
                    let res = ctx.resolve(Text("×").font(.system(size: u(10), weight: .bold)).foregroundStyle(palette.warnAmber))
                    ctx.draw(res, at: CGPoint(x: u(indCx), y: u(y)), anchor: .center)
                    continue
                }
                let midi = tuning.midi[s] + fret
                let degree = mod12(midi - rootPc)
                let color = ringColor(degree: degree)
                let x = fret == 0 ? indCx : fretX(fret)
                let r = u(Self.ringRadius)
                let rect = CGRect(x: u(x) - r, y: u(y) - r, width: r * 2, height: r * 2)
                ctx.fill(Path(ellipseIn: rect), with: .color(palette.boardGrey))
                ctx.stroke(Path(ellipseIn: rect), with: .color(color), lineWidth: u(2))

                let labelText = labels == .notes ? pcName(mod12(midi), flats: flats) : degreeLabels[degree]
                let res = ctx.resolve(Text(labelText).font(.system(size: u(10), weight: .bold)).foregroundStyle(palette.ink))
                ctx.draw(res, at: CGPoint(x: u(x), y: u(y)), anchor: .center)
            }
        }
        // Stretched, the canvas takes all the space it's offered. aspectRatio(nil) would size
        // it by the canvas's ideal ratio instead and leave the card's lower part empty.
        .modifier(DiagramSizing(stretch: stretch, ratio: naturalWidth / naturalHeight))
        .accessibilityLabel("\(symbol) fingering \(index + 1)")
    }
}

private struct DiagramSizing: ViewModifier {
    let stretch: Bool
    let ratio: CGFloat

    func body(content: Content) -> some View {
        if stretch {
            content.frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            content.aspectRatio(ratio, contentMode: .fit)
        }
    }
}
