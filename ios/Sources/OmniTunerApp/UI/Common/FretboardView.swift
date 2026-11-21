import OmniTunerCore
import SwiftUI

public struct SoundingNote: Equatable, Sendable {
    public let id: UUID
    public let midi: Int
    public let stringIndex: Int?
    public let fret: Int?

    public init(midi: Int, stringIndex: Int?, fret: Int?) {
        self.id = UUID()
        self.midi = midi
        self.stringIndex = stringIndex
        self.fret = fret
    }
}

public struct FretboardView: View {
    @Environment(\.omniPalette) private var palette

    public let board: [[FretCell]]
    public let useNoteNames: Bool
    public let showOutside: Bool
    public let sounding: SoundingNote?
    public let fitHeight: Bool
    public let onCellTap: (FretCell) -> Void

    private static let singleMarkers: Set<Int> = [3, 5, 7, 9, 15, 17, 19, 21]
    private static let doubleMarkers: Set<Int> = [12, 24]
    private static let openWidth: CGFloat = 40
    private static let topPad: CGFloat = 10
    private static let axisHeight: CGFloat = 20
    private static let nutWidth: CGFloat = 4
    private static let minFitColumn: CGFloat = 26
    private static let scrollColumn: CGFloat = 34
    private static let maxDotRadius: CGFloat = 14
    private static let minFitRow: CGFloat = 22
    private static let maxFitRow: CGFloat = 48

    @State private var pulseAmount: CGFloat = 0

    public init(
        board: [[FretCell]],
        useNoteNames: Bool,
        showOutside: Bool,
        sounding: SoundingNote?,
        fitHeight: Bool = false,
        onCellTap: @escaping (FretCell) -> Void
    ) {
        self.board = board
        self.useNoteNames = useNoteNames
        self.showOutside = showOutside
        self.sounding = sounding
        self.fitHeight = fitHeight
        self.onCellTap = onCellTap
    }

    private func rowHeightFor(strings: Int) -> CGFloat {
        if strings <= 6 { return 36 }
        if strings <= 8 { return 32 }
        return 28
    }

    private func isTappable(_ cell: FretCell?) -> Bool {
        guard let cell else { return false }
        return cell.interval != nil || showOutside
    }

    public var body: some View {
        if board.isEmpty {
            EmptyView()
        } else {
            let strings = board.count
            let fretCount = max(1, (board.first?.count ?? 1) - 1)

            GeometryReader { geo in
                let rowHeight: CGFloat = fitHeight
                    ? min(Self.maxFitRow, max(Self.minFitRow, (geo.size.height - Self.topPad - Self.axisHeight) / CGFloat(strings)))
                    : rowHeightFor(strings: strings)
                let boardHeight = Self.topPad + rowHeight * CGFloat(strings) + Self.axisHeight
                let fitColumn = (geo.size.width - Self.openWidth) / CGFloat(fretCount)
                let column = fitColumn >= Self.minFitColumn ? fitColumn : Self.scrollColumn
                let radius = min(column * 0.42, rowHeight * 0.4, Self.maxDotRadius)

                HStack(spacing: 0) {
                    openColumn(strings: strings, rowHeight: rowHeight, radius: radius)
                        .frame(width: Self.openWidth, height: boardHeight)

                    ScrollView(.horizontal, showsIndicators: false) {
                        fretsCanvas(strings: strings, fretCount: fretCount, rowHeight: rowHeight, colWidth: column, radius: radius)
                            .frame(width: column * CGFloat(fretCount), height: boardHeight)
                    }
                }
                .frame(height: boardHeight)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.boardGrey)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(palette.hairline, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .frame(height: fitHeight ? nil : Self.topPad + rowHeightFor(strings: strings) * CGFloat(strings) + Self.axisHeight)
            .onChange(of: sounding?.id) { _, newValue in
                guard newValue != nil else { return }
                pulseAmount = 1.0
                withAnimation(.easeOut(duration: 0.3)) {
                    pulseAmount = 0.0
                }
            }
        }
    }

    private func openColumn(strings: Int, rowHeight: CGFloat, radius: CGFloat) -> some View {
        Canvas { ctx, size in
            let top = Self.topPad
            drawStrings(ctx: &ctx, strings: strings, width: size.width, top: top, rowHeight: rowHeight)
            let nutX = size.width - Self.nutWidth / 2
            var nutPath = Path()
            nutPath.move(to: CGPoint(x: nutX, y: top))
            nutPath.addLine(to: CGPoint(x: nutX, y: top + rowHeight * CGFloat(strings)))
            ctx.stroke(nutPath, with: .color(palette.mutedLabel), lineWidth: Self.nutWidth)

            let cx = (size.width - Self.nutWidth) / 2
            for (s, row) in board.enumerated() {
                guard let cell = row.first else { continue }
                let cy = top + rowHeight * (CGFloat(s) + 0.5)
                if cell.interval == nil && !showOutside {
                    let gapRect = CGRect(x: cx - radius * 0.8, y: cy - radius * 0.8, width: radius * 1.6, height: radius * 1.6)
                    ctx.fill(Path(ellipseIn: gapRect), with: .color(palette.boardGrey))
                    drawText(ctx: &ctx, text: cell.noteName, at: CGPoint(x: cx, y: cy), color: palette.mutedLabel, bold: false)
                } else {
                    drawNote(ctx: &ctx, cell: cell, center: CGPoint(x: cx, y: cy), radius: radius)
                }
            }
            let axisY = top + rowHeight * CGFloat(strings) + Self.axisHeight / 2
            drawText(ctx: &ctx, text: "0", at: CGPoint(x: cx, y: axisY), color: palette.mutedLabel, bold: false)
        }
        .contentShape(Rectangle())
        .onTapGesture { location in
            guard location.y >= Self.topPad else { return }
            let row = Int((location.y - Self.topPad) / rowHeight)
            if row >= 0 && row < board.count, let cell = board[row].first, isTappable(cell) {
                onCellTap(cell)
            }
        }
    }

    private func fretsCanvas(strings: Int, fretCount: Int, rowHeight: CGFloat, colWidth: CGFloat, radius: CGFloat) -> some View {
        Canvas { ctx, _ in
            let top = Self.topPad
            let bottom = top + rowHeight * CGFloat(strings)
            func centerX(_ fret: Int) -> CGFloat { colWidth * (CGFloat(fret) - 0.5) }

            let inlayR: CGFloat = 4
            for f in 1...fretCount {
                let cx = centerX(f)
                if Self.doubleMarkers.contains(f) {
                    for ratio in [0.3, 0.7] {
                        let rect = CGRect(x: cx - inlayR, y: top + ratio * (bottom - top) - inlayR, width: inlayR * 2, height: inlayR * 2)
                        ctx.fill(Path(ellipseIn: rect), with: .color(palette.hairline))
                    }
                } else if Self.singleMarkers.contains(f) {
                    let rect = CGRect(x: cx - inlayR, y: (top + bottom) / 2 - inlayR, width: inlayR * 2, height: inlayR * 2)
                    ctx.fill(Path(ellipseIn: rect), with: .color(palette.hairline))
                }
                let x = colWidth * CGFloat(f)
                var fretPath = Path()
                fretPath.move(to: CGPoint(x: x, y: top))
                fretPath.addLine(to: CGPoint(x: x, y: bottom))
                ctx.stroke(fretPath, with: .color(palette.hairline), lineWidth: 1)
            }

            drawStrings(ctx: &ctx, strings: strings, width: colWidth * CGFloat(fretCount), top: top, rowHeight: rowHeight)

            for (s, row) in board.enumerated() {
                let cy = top + rowHeight * (CGFloat(s) + 0.5)
                for cell in row.dropFirst() {
                    if cell.interval == nil && !showOutside { continue }
                    drawNote(ctx: &ctx, cell: cell, center: CGPoint(x: centerX(cell.fret), y: cy), radius: radius)
                }
            }

            let axisY = bottom + Self.axisHeight / 2
            for f in 1...fretCount {
                drawText(ctx: &ctx, text: "\(f)", at: CGPoint(x: centerX(f), y: axisY), color: palette.mutedLabel, bold: false)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { location in
            guard location.y >= Self.topPad else { return }
            let fret = Int(location.x / colWidth) + 1
            let row = Int((location.y - Self.topPad) / rowHeight)
            if row >= 0 && row < board.count && fret >= 1 && fret < board[row].count {
                let cell = board[row][fret]
                if isTappable(cell) { onCellTap(cell) }
            }
        }
    }

    private func drawStrings(ctx: inout GraphicsContext, strings: Int, width: CGFloat, top: CGFloat, rowHeight: CGFloat) {
        for s in 0..<strings {
            let y = top + rowHeight * (CGFloat(s) + 0.5)
            let weight: CGFloat = 0.75 + 1.25 * CGFloat(s) / CGFloat(max(1, strings - 1))
            var path = Path()
            path.move(to: CGPoint(x: 0, y: y))
            path.addLine(to: CGPoint(x: width, y: y))
            ctx.stroke(path, with: .color(palette.mutedLabel.opacity(0.65)), lineWidth: weight)
        }
    }

    private func drawNote(ctx: inout GraphicsContext, cell: FretCell, center: CGPoint, radius: CGFloat) {
        let interval = cell.interval
        let isSounding = sounding != nil && sounding?.stringIndex == cell.stringIndex && sounding?.fret == cell.fret
        let label: String? = if let interval {
            useNoteNames ? cell.noteName : interval.label
        } else {
            useNoteNames ? cell.noteName : nil
        }

        if isSounding {
            let r = radius * (1.0 + 0.15 * pulseAmount)
            let haloRect = CGRect(x: center.x - r * 1.45, y: center.y - r * 1.45, width: r * 2.9, height: r * 2.9)
            ctx.fill(Path(ellipseIn: haloRect), with: .color(palette.accent.opacity(0.25)))
            let fillRect = CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)
            ctx.fill(Path(ellipseIn: fillRect), with: .color(palette.accent))
            if let label { drawText(ctx: &ctx, text: label, at: center, color: palette.onAccent, bold: true) }
        } else if interval == nil {
            let gapR = radius * 0.8
            let gapRect = CGRect(x: center.x - gapR, y: center.y - gapR, width: gapR * 2, height: gapR * 2)
            ctx.fill(Path(ellipseIn: gapRect), with: .color(palette.boardGrey))
            if let label { drawText(ctx: &ctx, text: label, at: center, color: palette.mutedLabel, bold: false) }
        } else if cell.isRoot {
            let fillRect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            ctx.fill(Path(ellipseIn: fillRect), with: .color(palette.accent))
            if let label { drawText(ctx: &ctx, text: label, at: center, color: palette.onAccent, bold: true) }
        } else {
            let fillRect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            ctx.fill(Path(ellipseIn: fillRect), with: .color(palette.boardGrey))
            let strokeR = radius - 0.75
            let strokeRect = CGRect(x: center.x - strokeR, y: center.y - strokeR, width: strokeR * 2, height: strokeR * 2)
            ctx.stroke(Path(ellipseIn: strokeRect), with: .color(palette.ink), lineWidth: 1.5)
            if let label { drawText(ctx: &ctx, text: label, at: center, color: palette.ink, bold: true) }
        }
    }

    private func drawText(ctx: inout GraphicsContext, text: String, at point: CGPoint, color: Color, bold: Bool) {
        let resolved = ctx.resolve(
            Text(text)
                .font(.system(size: 11, weight: bold ? .bold : .regular))
                .foregroundStyle(color)
        )
        ctx.draw(resolved, at: point, anchor: .center)
    }
}
