package com.pratap.filesharingapp.codec

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

internal const val MASK32 = 0xFFFFFFFFL

/** xorshift32, see protocol/spec.md section 3.2. Outputs are u32 held in a Long. */
class XorShift32(seed: Long) {
    private var x: Int = seed.toInt()

    fun next(): Long {
        var s = x
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        x = s
        return s.toLong() and MASK32
    }
}

object Fountain {
    fun packetSeed(esi: Long, session: Long): Long {
        // Long multiplication wraps mod 2^64; the low 32 bits are what we keep.
        var s = (esi * 0x9E3779B1L + session) and MASK32
        s = s xor (s ushr 16)
        return if (s == 0L) 0x6D2B79F5L else s
    }

    /** Robust soliton CDF; element d-1 is P(degree <= d). */
    fun solitonCdf(n: Int, c: Double = 0.1, delta: Double = 0.5): DoubleArray {
        if (n == 1) return doubleArrayOf(1.0)
        val rho = DoubleArray(n + 1)
        rho[1] = 1.0 / n
        for (d in 2..n) rho[d] = 1.0 / (d.toDouble() * (d - 1))
        val r = c * ln(n / delta) * sqrt(n.toDouble())
        val spike = floor(n / r).toInt().coerceIn(1, n)
        val tau = DoubleArray(n + 1)
        for (d in 1 until spike) tau[d] = r / (d.toDouble() * n)
        tau[spike] = maxOf(0.0, r * ln(r / delta) / n)
        var total = 0.0
        for (d in 1..n) total += rho[d] + tau[d]
        val cdf = DoubleArray(n)
        var acc = 0.0
        for (d in 1..n) {
            acc += (rho[d] + tau[d]) / total
            cdf[d - 1] = acc
        }
        cdf[n - 1] = 1.0
        return cdf
    }

    /** Encoder side: picks the degree from [cdf] and the block indices. */
    fun encoderIndices(esi: Long, session: Long, n: Int, cdf: DoubleArray): IntArray {
        if (esi < n) return intArrayOf(esi.toInt())
        val rng = XorShift32(packetSeed(esi, session))
        val u = rng.next().toDouble() / 4294967296.0
        var degree = n
        for (d in 1..n) if (cdf[d - 1] >= u) { degree = d; break }
        return draw(rng, degree, n)
    }

    /** Decoder side: the degree comes from the frame header. */
    fun decoderIndices(esi: Long, session: Long, n: Int, degree: Int): IntArray {
        if (esi < n) return intArrayOf(esi.toInt())
        val rng = XorShift32(packetSeed(esi, session))
        rng.next() // degree draw, discarded
        return draw(rng, degree, n)
    }

    private fun draw(rng: XorShift32, degree: Int, n: Int): IntArray {
        val chosen = IntArray(degree)
        var count = 0
        while (count < degree) {
            val i = (rng.next() % n).toInt()
            var dup = false
            for (j in 0 until count) if (chosen[j] == i) { dup = true; break }
            if (!dup) chosen[count++] = i
        }
        return chosen
    }
}
