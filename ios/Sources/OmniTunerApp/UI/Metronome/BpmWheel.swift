import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Custom folding tempo drum matching Android's `BpmWheel`:
/// at rest (`collapse == 1`) it renders a large hero readout; dragging vertically unfolds a 3D curved
/// drum of neighboring tempos with haptic step ticks and inertial fling, then settles on a whole BPM.
public struct BpmWheel: View {
    @Environment(\.omniPalette) private var palette

    public let value: Int
    public let range: ClosedRange<Int>
    public let centerSize: CGFloat
    public let pitch: CGFloat
    public let compactScale: CGFloat
    public let collapse: CGFloat
    public let centerScale: CGFloat
    public let centerColor: Color
    public let onValueChange: (Int) -> Void
    public let onCenterTap: () -> Void
    public let onInteractionChange: (Bool) -> Void

    @State private var position: Double
    @State private var interacting = false
    @State private var didDrag = false
    @State private var dragBasePosition: Double? = nil
    @State private var lastTickRow: Int
    @State private var animTask: Task<Void, Never>? = nil
    @State private var isGestureAnimating = false
    #if canImport(UIKit)
    @State private var selectionHaptic = UISelectionFeedbackGenerator()
    #endif

    public init(
        value: Int,
        range: ClosedRange<Int>,
        centerSize: CGFloat,
        pitch: CGFloat,
        compactScale: CGFloat = 1.45,
        collapse: CGFloat = 1.0,
        centerScale: CGFloat = 1.0,
        centerColor: Color,
        onValueChange: @escaping (Int) -> Void,
        onCenterTap: @escaping () -> Void,
        onInteractionChange: @escaping (Bool) -> Void = { _ in }
    ) {
        let clamped = min(range.upperBound, max(range.lowerBound, value))
        let initialRow = Double(clamped - range.lowerBound)
        self.value = clamped
        self.range = range
        self.centerSize = centerSize
        self.pitch = pitch
        self.compactScale = compactScale
        self.collapse = collapse
        self.centerScale = centerScale
        self.centerColor = centerColor
        self.onValueChange = onValueChange
        self.onCenterTap = onCenterTap
        self.onInteractionChange = onInteractionChange
        _position = State(initialValue: initialRow)
        _lastTickRow = State(initialValue: Int(initialRow))
    }

    private var maxIndex: Int {
        max(0, range.upperBound - range.lowerBound)
    }

    public var body: some View {
        GeometryReader { geo in
            let halfHeight = max(1, geo.size.height / 2)
            let geometry = DrumGeometry(bigPx: centerSize, pitchPx: pitch, halfHeight: halfHeight)
            let centerRow = min(maxIndex, max(0, Int(position.rounded())))
            let firstRow = max(0, centerRow - geometry.visibleRows)
            let lastRow = min(maxIndex, centerRow + geometry.visibleRows)

            DrumWheelRows(
                position: position,
                centerRow: centerRow,
                firstRow: firstRow,
                lastRow: lastRow,
                rangeLowerBound: range.lowerBound,
                geometry: geometry,
                centerSize: centerSize,
                compactScale: compactScale,
                collapse: collapse,
                centerScale: centerScale,
                centerColor: centerColor,
                inkColor: palette.ink,
                size: geo.size
            )
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { drag in
                        if !didDrag {
                            guard abs(drag.translation.height) >= 5 else { return }
                            animTask?.cancel()
                            animTask = nil
                            isGestureAnimating = false
                            didDrag = true
                            dragBasePosition = position
                            setInteracting(true)
                            #if canImport(UIKit)
                            selectionHaptic.prepare()
                            #endif
                        }
                        let base = dragBasePosition ?? position
                        let next = min(Double(maxIndex), max(0.0, base - Double(drag.translation.height / pitch)))
                        position = next
                        tickIfRowChanged(next)
                    }
                    .onEnded { drag in
                        if didDrag {
                            didDrag = false
                            dragBasePosition = nil
                            let velocityRows = -Double(drag.velocity.height / pitch)
                            launchFling(velocityRows: velocityRows)
                        } else {
                            dragBasePosition = nil
                            let dy = drag.location.y - halfHeight
                            let isFolded = collapse > 0.5
                            let centerHalf = (centerSize / 2) * (isFolded ? compactScale : 1.0)
                            if abs(dy) <= centerHalf {
                                onCenterTap()
                                return
                            }
                            guard !isFolded else { return }
                            let tappedRow = (firstRow...lastRow).min { a, b in
                                abs(geometry.offset(Double(a) - position) - dy)
                                    < abs(geometry.offset(Double(b) - position) - dy)
                            }
                            if let tappedRow {
                                setInteracting(true)
                                launchSettle(to: tappedRow)
                            }
                        }
                    }
            )
        }
        .onChange(of: value) { _, newValue in
            guard !interacting, !isGestureAnimating else { return }
            let target = Double(min(maxIndex, max(0, newValue - range.lowerBound)))
            guard abs(position - target) > 0.001 else { return }
            animTask?.cancel()
            animTask = nil
            if collapse > 0.5 {
                position = target
                lastTickRow = Int(target.rounded())
            } else {
                launchSync(to: target)
            }
        }
        .onDisappear {
            animTask?.cancel()
            animTask = nil
            isGestureAnimating = false
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Tempo")
        .accessibilityValue("\(value) BPM")
        .accessibilityAddTraits(.isButton)
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                let next = min(range.upperBound, value + 1)
                if next != value { onValueChange(next) }
            case .decrement:
                let next = max(range.lowerBound, value - 1)
                if next != value { onValueChange(next) }
            @unknown default:
                break
            }
        }
    }

    // MARK: - Interaction & Motion

    private func setInteracting(_ active: Bool) {
        guard interacting != active else { return }
        interacting = active
        onInteractionChange(active)
    }

    private func tickIfRowChanged(_ pos: Double) {
        let row = min(maxIndex, max(0, Int(pos.rounded())))
        guard row != lastTickRow else { return }
        lastTickRow = row
        #if canImport(UIKit)
        selectionHaptic.selectionChanged()
        #endif
    }

    private func commit(row: Int) {
        let clampedRow = min(maxIndex, max(0, row))
        position = Double(clampedRow)
        lastTickRow = clampedRow
        setInteracting(false)
        let committed = range.lowerBound + clampedRow
        if committed != value {
            onValueChange(committed)
        }
    }

    private func launchFling(velocityRows: Double) {
        animTask?.cancel()
        isGestureAnimating = true
        let natural = position + (abs(velocityRows) > Self.minFlingVelocity ? velocityRows / Self.flingFriction : 0)
        let target = min(maxIndex, max(0, Int(natural.rounded())))
        let startPos = position
        let distance = Double(target) - startPos

        animTask = Task { @MainActor in
            let clock = ContinuousClock()
            var lastTime = clock.now

            if abs(velocityRows) > Self.minFlingVelocity,
               abs(distance) > 0.35,
               (distance > 0) == (velocityRows > 0) {
                var v = velocityRows
                let effFriction = abs(velocityRows) / abs(distance)
                let startCoast = clock.now
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(8))
                    if Task.isCancelled { return }
                    let now = clock.now
                    let dt = min(0.05, max(0.001, durationSeconds(lastTime.duration(to: now))))
                    lastTime = now
                    v *= exp(-effFriction * dt)
                    let nextPos = min(Double(maxIndex), max(0.0, position + v * dt))
                    position = nextPos
                    tickIfRowChanged(nextPos)
                    let elapsed = durationSeconds(startCoast.duration(to: now))
                    if abs(v) < 1.2 || abs(Double(target) - nextPos) < 0.08 || elapsed > 1.4 {
                        break
                    }
                }
            }

            await runSettleLoop(to: target, haptic: true, fromTime: lastTime, clock: clock)
            if !Task.isCancelled {
                animTask = nil
                isGestureAnimating = false
                commit(row: target)
            }
        }
    }

    private func launchSettle(to target: Int) {
        animTask?.cancel()
        isGestureAnimating = true
        animTask = Task { @MainActor in
            let clock = ContinuousClock()
            await runSettleLoop(to: target, haptic: true, fromTime: clock.now, clock: clock)
            if !Task.isCancelled {
                animTask = nil
                isGestureAnimating = false
                commit(row: target)
            }
        }
    }

    private func launchSync(to target: Double) {
        animTask?.cancel()
        let start = position
        animTask = Task { @MainActor in
            let clock = ContinuousClock()
            let startTime = clock.now
            let duration = 0.20
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(8))
                if Task.isCancelled { return }
                let elapsed = durationSeconds(startTime.duration(to: clock.now))
                let t = min(1.0, elapsed / duration)
                let eased = 1.0 - pow(1.0 - t, 3)
                position = start + (target - start) * eased
                if t >= 1.0 { break }
            }
            if !Task.isCancelled {
                position = target
                lastTickRow = Int(target.rounded())
                animTask = nil
            }
        }
    }

    @MainActor
    private func runSettleLoop(
        to target: Int,
        haptic: Bool,
        fromTime: ContinuousClock.Instant,
        clock: ContinuousClock
    ) async {
        let startPos = position
        let targetPos = Double(target)
        guard abs(targetPos - startPos) > 0.002 else {
            position = targetPos
            if haptic { tickIfRowChanged(targetPos) }
            return
        }
        let duration = min(0.22, max(0.08, abs(targetPos - startPos) * 0.04))
        let startTime = fromTime
        while !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(8))
            if Task.isCancelled { return }
            let elapsed = durationSeconds(startTime.duration(to: clock.now))
            let t = min(1.0, elapsed / duration)
            let eased = 1.0 - pow(1.0 - t, 3)
            let next = startPos + (targetPos - startPos) * eased
            position = next
            if haptic { tickIfRowChanged(next) }
            if t >= 1.0 { break }
        }
    }

    private func durationSeconds(_ d: Duration) -> Double {
        let c = d.components
        return Double(c.seconds) + Double(c.attoseconds) * 1e-18
    }

    private static let flingFriction: Double = 3.2
    private static let minFlingVelocity: Double = 1.5
}

private struct DrumWheelRows: View, @MainActor Animatable {
    let position: Double
    let centerRow: Int
    let firstRow: Int
    let lastRow: Int
    let rangeLowerBound: Int
    let geometry: DrumGeometry
    let centerSize: CGFloat
    let compactScale: CGFloat
    var collapse: CGFloat
    var centerScale: CGFloat
    let centerColor: Color
    let inkColor: Color
    let size: CGSize

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(collapse, centerScale) }
        set {
            collapse = newValue.first
            centerScale = newValue.second
        }
    }

    var body: some View {
        ZStack {
            ForEach(firstRow...lastRow, id: \.self) { row in
                let d = Double(row) - position
                let isCenter = abs(d) < 0.5
                let focus = geometry.focus(d)
                let angle = geometry.angle(d)
                let yOffset = geometry.offset(d) * (1.0 - collapse)
                let foldScale = isCenter
                    ? (1.0 + (compactScale - 1.0) * collapse)
                    : (1.0 + (Self.foldedNeighbourScale - 1.0) * collapse)
                let beatScale = 1.0 + (centerScale - 1.0) * focus
                let totalScale = geometry.scale(d) * geometry.depth(angle) * beatScale * foldScale
                let foldAlpha = isCenter ? 1.0 : (1.0 - collapse) * (1.0 - collapse)
                let alpha = (Self.neighbourAlpha + (1.0 - Self.neighbourAlpha) * focus)
                    * max(0.0, cos(angle))
                    * foldAlpha

                Text("\(rangeLowerBound + row)")
                    .font(.system(size: centerSize * compactScale, weight: .bold).monospacedDigit())
                    .lineLimit(1)
                    .fixedSize()
                    .foregroundStyle(row == centerRow ? centerColor : inkColor)
                    .scaleEffect(totalScale / compactScale)
                    .rotation3DEffect(.radians(-angle), axis: (x: 1, y: 0, z: 0), perspective: 0.45)
                    .offset(y: yOffset)
                    .opacity(alpha)
            }
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .mask(
            LinearGradient(
                stops: [
                    .init(color: .black.opacity(collapse), location: 0.0),
                    .init(color: .black, location: Self.edgeFade),
                    .init(color: .black, location: 1.0 - Self.edgeFade),
                    .init(color: .black.opacity(collapse), location: 1.0),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private static let neighbourAlpha: Double = 0.45
    private static let edgeFade: Double = 0.12
    private static let foldedNeighbourScale: Double = 0.6
}

// MARK: - Drum Geometry

private struct DrumGeometry {
    private let bigPx: Double
    private let pitchPx: Double
    private let radius: Double
    private let smallPx: Double
    let visibleRows: Int

    private static let maxAngle = Double.pi / 2.0
    private static let maxVisibleRows = 30
    private static let sizeFalloff = 1.2
    private static let neighbourRatio = 0.62
    private static let drumRadiusShare = 0.9
    private static let depthShrink = 0.4

    init(bigPx: CGFloat, pitchPx: CGFloat, halfHeight: CGFloat) {
        self.bigPx = Double(bigPx)
        self.pitchPx = Double(pitchPx)
        self.radius = max(1.0, Double(halfHeight) * Self.drumRadiusShare)
        self.smallPx = Self.neighbourRatio * Double(pitchPx)

        var rows = 1
        while rows < Self.maxVisibleRows && abs(Self.arc(Double(rows), pitchPx: Double(pitchPx), bigPx: Double(bigPx), smallPx: Self.neighbourRatio * Double(pitchPx))) < self.radius * Self.maxAngle {
            rows += 1
        }
        self.visibleRows = rows
    }

    func focus(_ d: Double) -> Double {
        let c = cosh(d / Self.sizeFalloff)
        return 1.0 / (c * c)
    }

    private static func arc(_ d: Double, pitchPx: Double, bigPx: Double, smallPx: Double) -> Double {
        let signD = d < 0 ? -1.0 : 1.0
        return signD * (pitchPx * abs(d) + (bigPx - smallPx) * sizeFalloff * tanh(abs(d) / sizeFalloff))
    }

    func angle(_ d: Double) -> Double {
        let a = Self.arc(d, pitchPx: pitchPx, bigPx: bigPx, smallPx: smallPx) / radius
        return min(Self.maxAngle, max(-Self.maxAngle, a))
    }

    func offset(_ d: Double) -> Double {
        radius * sin(angle(d))
    }

    func scale(_ d: Double) -> Double {
        let minScale = bigPx > 0 ? (smallPx / bigPx) : 0.5
        return minScale + (1.0 - minScale) * focus(d)
    }

    func depth(_ angle: Double) -> Double {
        1.0 / (1.0 + Self.depthShrink * (1.0 - cos(angle)))
    }
}
