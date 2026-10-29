package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * YIN's difference function d(tau) = E(window) + E(shifted window) - 2 * corr(tau), with the cross
 * correlation done by FFT. Identical to the brute-force sum but far cheaper for long lag ranges.
 */
class YinDifference {
    private var size = 0
    private var aRe = DoubleArray(0)
    private var aIm = DoubleArray(0)
    private var bRe = DoubleArray(0)
    private var bIm = DoubleArray(0)
    private var cosTable = DoubleArray(0)
    private var sinTable = DoubleArray(0)
    private var prefix = DoubleArray(0)

    /** Fills out[1..maxLag] with d(tau) for a window of n - maxLag samples. */
    fun compute(buffer: FloatArray, maxLag: Int, out: DoubleArray) {
        val n = buffer.size
        val w = n - maxLag

        if (prefix.size < n + 1) prefix = DoubleArray(n + 1)
        prefix[0] = 0.0
        for (i in 0 until n) {
            val v = buffer[i].toDouble()
            prefix[i + 1] = prefix[i] + v * v
        }
        val windowEnergy = prefix[w]

        var s = 1
        while (s < n + w) s = s shl 1
        ensureSize(s)

        for (i in 0 until s) {
            aRe[i] = if (i < w) buffer[i].toDouble() else 0.0
            aIm[i] = 0.0
            bRe[i] = if (i < n) buffer[i].toDouble() else 0.0
            bIm[i] = 0.0
        }
        transform(aRe, aIm, inverse = false)
        transform(bRe, bIm, inverse = false)
        // conj(A) * B, so the inverse transform yields sum_j a[j] * b[j + tau].
        for (i in 0 until s) {
            val re = aRe[i] * bRe[i] + aIm[i] * bIm[i]
            val im = aRe[i] * bIm[i] - aIm[i] * bRe[i]
            aRe[i] = re
            aIm[i] = im
        }
        transform(aRe, aIm, inverse = true)

        for (tau in 1..maxLag) {
            val shiftedEnergy = prefix[tau + w] - prefix[tau]
            val d = windowEnergy + shiftedEnergy - 2.0 * aRe[tau]
            out[tau] = if (d > 0.0) d else 0.0
        }
    }

    private fun ensureSize(s: Int) {
        if (s == size) return
        size = s
        aRe = DoubleArray(s)
        aIm = DoubleArray(s)
        bRe = DoubleArray(s)
        bIm = DoubleArray(s)
        cosTable = DoubleArray(s / 2) { cos(2.0 * PI * it / s) }
        sinTable = DoubleArray(s / 2) { sin(2.0 * PI * it / s) }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = if (inverse) sinTable[k * step] else -sinTable[k * step]
                    val i1 = start + k
                    val i2 = i1 + half
                    val xr = re[i2] * wr - im[i2] * wi
                    val xi = re[i2] * wi + im[i2] * wr
                    re[i2] = re[i1] - xr
                    im[i2] = im[i1] - xi
                    re[i1] += xr
                    im[i1] += xi
                }
                start += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
