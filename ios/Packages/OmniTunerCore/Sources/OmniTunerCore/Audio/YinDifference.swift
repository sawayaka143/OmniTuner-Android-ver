#if canImport(Accelerate)
import Accelerate
#endif
import Foundation

public final class YinDifference {
#if canImport(Accelerate)
    private var size = 0
    private var fftSetup: FFTSetupD?
    private var log2n: vDSP_Length = 0
    private var aReal: [Double] = []
    private var aImag: [Double] = []
    private var bReal: [Double] = []
    private var bImag: [Double] = []
    private var prefix: [Double] = []

    public init() {}

    deinit {
        if let fftSetup {
            vDSP_destroy_fftsetupD(fftSetup)
        }
    }

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
        guard let fftSetup else { return }

        let half = s / 2
        for j in 0..<half {
            let i0 = 2 * j
            let i1 = i0 + 1
            aReal[j] = i0 < w ? Double(buffer[i0]) : 0.0
            aImag[j] = i1 < w ? Double(buffer[i1]) : 0.0
            bReal[j] = i0 < n ? Double(buffer[i0]) : 0.0
            bImag[j] = i1 < n ? Double(buffer[i1]) : 0.0
        }

        aReal.withUnsafeMutableBufferPointer { aReBuf in
            aImag.withUnsafeMutableBufferPointer { aImBuf in
                bReal.withUnsafeMutableBufferPointer { bReBuf in
                    bImag.withUnsafeMutableBufferPointer { bImBuf in
                        guard let aRePtr = aReBuf.baseAddress,
                              let aImPtr = aImBuf.baseAddress,
                              let bRePtr = bReBuf.baseAddress,
                              let bImPtr = bImBuf.baseAddress else { return }

                        var aSplit = DSPDoubleSplitComplex(realp: aRePtr, imagp: aImPtr)
                        var bSplit = DSPDoubleSplitComplex(realp: bRePtr, imagp: bImPtr)
                        var cSplit = DSPDoubleSplitComplex(realp: aRePtr, imagp: aImPtr)

                        vDSP_fft_zripD(fftSetup, &aSplit, 1, log2n, FFTDirection(kFFTDirection_Forward))
                        vDSP_fft_zripD(fftSetup, &bSplit, 1, log2n, FFTDirection(kFFTDirection_Forward))

                        let aDC = aRePtr[0]
                        let aNyq = aImPtr[0]
                        let bDC = bRePtr[0]
                        let bNyq = bImPtr[0]

                        vDSP_zvmulD(&aSplit, 1, &bSplit, 1, &cSplit, 1, vDSP_Length(half), -1)
                        aRePtr[0] = aDC * bDC
                        aImPtr[0] = aNyq * bNyq

                        vDSP_fft_zripD(fftSetup, &cSplit, 1, log2n, FFTDirection(kFFTDirection_Inverse))
                    }
                }
            }
        }

        let invScale = 1.0 / (4.0 * Double(s))
        for tau in 1...maxLag {
            let r = ((tau & 1) == 0 ? aReal[tau >> 1] : aImag[tau >> 1]) * invScale
            let shiftedEnergy = prefix[tau + w] - prefix[tau]
            let d = windowEnergy + shiftedEnergy - 2.0 * r
            out[tau] = d > 0.0 ? d : 0.0
        }
    }

    private func ensureSize(_ s: Int) {
        if s == size { return }
        if let fftSetup {
            vDSP_destroy_fftsetupD(fftSetup)
            self.fftSetup = nil
        }
        size = s
        log2n = vDSP_Length(s.trailingZeroBitCount)
        fftSetup = vDSP_create_fftsetupD(log2n, FFTRadix(kFFTRadix2))
        let half = s / 2
        aReal = [Double](repeating: 0.0, count: half)
        aImag = [Double](repeating: 0.0, count: half)
        bReal = [Double](repeating: 0.0, count: half)
        bImag = [Double](repeating: 0.0, count: half)
    }
#else
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
#endif
}
