package com.pratap.filesharingapp.codec

/** Base45 as defined in RFC 9285. Output uses only the QR alphanumeric charset. */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private val REVERSE = IntArray(128) { -1 }.also { r ->
        ALPHABET.forEachIndexed { i, c -> r[c.code] = i }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size / 2) * 3 + 2)
        var i = 0
        while (i + 1 < data.size) {
            var n = (data[i].toInt() and 0xFF) * 256 + (data[i + 1].toInt() and 0xFF)
            val c = n % 45; n /= 45
            val d = n % 45; val e = n / 45
            sb.append(ALPHABET[c]).append(ALPHABET[d]).append(ALPHABET[e])
            i += 2
        }
        if (data.size % 2 == 1) {
            val n = data.last().toInt() and 0xFF
            sb.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return sb.toString()
    }

    /** Returns null if [text] is not valid Base45. */
    fun decode(text: String): ByteArray? {
        if (text.length % 3 == 1) return null
        val out = ByteArray((text.length / 3) * 2 + if (text.length % 3 == 2) 1 else 0)
        var o = 0
        var i = 0
        while (i < text.length) {
            val remaining = text.length - i
            if (remaining >= 3) {
                val n = v(text[i]) + v(text[i + 1]) * 45 + v(text[i + 2]) * 2025
                if (n < 0 || n > 0xFFFF) return null
                out[o++] = (n shr 8).toByte()
                out[o++] = n.toByte()
                i += 3
            } else {
                val n = v(text[i]) + v(text[i + 1]) * 45
                if (n < 0 || n > 0xFF) return null
                out[o++] = n.toByte()
                i += 2
            }
        }
        return out
    }

    // Invalid chars map to a large negative value so any sum stays negative.
    private fun v(c: Char): Int {
        val code = c.code
        val r = if (code < 128) REVERSE[code] else -1
        return if (r < 0) -1_000_000 else r
    }
}
