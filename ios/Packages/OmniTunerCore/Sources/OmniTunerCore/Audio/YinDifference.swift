import Foundation

public final class YinDifference {
    private var size = 0
    private var aRe: [Double] = []
    private var aIm: [Double] = []
    private var bRe: [Double] = []
    private var bIm: [Double] = []
    private var cosTable: [Double] = []
    private var sinTable: [Double] = []
    private var prefix: [Double] = []

    public init() {}

    public func compute(buffer: [Float], maxLag: Int, out: inout [Double]) {
        let n = buffer.count
        let w = n - maxLag

        if prefix.count < n + 1 {
            prefix = [Double](repeating: 0.0, count: n + 1)
        }
        prefix[0] = 0.0
        for i in 0..<n {
            let v = Double(buffer[i])
            prefix[i + 1] = prefix[i] + v * v
        }
        let windowEnergy = prefix[w]

        var s = 1
        while s < n + w {
            s <<= 1
        }
        ensureSize(s)

        for i in 0..<s {
            aRe[i] = i < w ? Double(buffer[i]) : 0.0
            aIm[i] = 0.0
            bRe[i] = i < n ? Double(buffer[i]) : 0.0
            bIm[i] = 0.0
        }
        transform(&aRe, &aIm, inverse: false)
        transform(&bRe, &bIm, inverse: false)
        for i in 0..<s {
            let re = aRe[i] * bRe[i] + aIm[i] * bIm[i]
            let im = aRe[i] * bIm[i] - aIm[i] * bRe[i]
            aRe[i] = re
            aIm[i] = im
        }
        transform(&aRe, &aIm, inverse: true)

        for tau in 1...maxLag {
            let shiftedEnergy = prefix[tau + w] - prefix[tau]
            let d = windowEnergy + shiftedEnergy - 2.0 * aRe[tau]
            out[tau] = d > 0.0 ? d : 0.0
        }
    }

    private func ensureSize(_ s: Int) {
        if s == size { return }
        size = s
        aRe = [Double](repeating: 0.0, count: s)
        aIm = [Double](repeating: 0.0, count: s)
        bRe = [Double](repeating: 0.0, count: s)
        bIm = [Double](repeating: 0.0, count: s)
        cosTable = (0..<(s / 2)).map { cos(2.0 * .pi * Double($0) / Double(s)) }
        sinTable = (0..<(s / 2)).map { sin(2.0 * .pi * Double($0) / Double(s)) }
    }

    private func transform(_ re: inout [Double], _ im: inout [Double], inverse: Bool) {
        let n = size
        var j = 0
        for i in 1..<n {
            var bit = n >> 1
            while (j & bit) != 0 {
                j ^= bit
                bit >>= 1
            }
            j ^= bit
            if i < j {
                re.swapAt(i, j)
                im.swapAt(i, j)
            }
        }
        var len = 2
        while len <= n {
            let half = len / 2
            let step = n / len
            var start = 0
            while start < n {
                for k in 0..<half {
                    let wr = cosTable[k * step]
                    let wi = inverse ? sinTable[k * step] : -sinTable[k * step]
                    let i1 = start + k
                    let i2 = i1 + half
                    let xr = re[i2] * wr - im[i2] * wi
                    let xi = re[i2] * wi + im[i2] * wr
                    re[i2] = re[i1] - xr
                    im[i2] = im[i1] - xi
                    re[i1] += xr
                    im[i1] += xi
                }
                start += len
            }
            len <<= 1
        }
        if inverse {
            let invN = 1.0 / Double(n)
            for i in 0..<n {
                re[i] *= invN
                im[i] *= invN
            }
        }
    }
}
