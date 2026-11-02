package com.pratap.filesharingapp.codec

import java.nio.ByteBuffer
import java.util.zip.CRC32

/** One QR frame, see protocol/spec.md section 4. u32 fields are held in Longs. */
class Frame(
    val session: Long,
    val bundleLength: Long,
    val blockSize: Int,
    val esi: Long,
    val degree: Int,
    val payload: ByteArray,
) {
    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER + blockSize + 4) // big-endian by default
        buf.put('F'.code.toByte()).put('Q'.code.toByte()).put(VERSION.toByte()).put(0)
        buf.putInt(session.toInt())
        buf.putInt(bundleLength.toInt())
        buf.putShort(blockSize.toShort())
        buf.putInt(esi.toInt())
        buf.putShort(degree.toShort())
        buf.put(payload)
        val crc = CRC32().apply { update(buf.array(), 0, HEADER + blockSize) }.value
        buf.putInt(crc.toInt())
        return buf.array()
    }

    fun toText(): String = Base45.encode(toBytes())

    companion object {
        const val HEADER = 20
        const val VERSION = 1

        /** Returns null for anything that is not a valid v1 frame. */
        fun parse(bytes: ByteArray): Frame? {
            if (bytes.size < HEADER + 4) return null
            if (bytes[0] != 'F'.code.toByte() || bytes[1] != 'Q'.code.toByte() || bytes[2].toInt() != VERSION) return null
            val buf = ByteBuffer.wrap(bytes)
            val session = buf.getInt(4).toLong() and MASK32
            val length = buf.getInt(8).toLong() and MASK32
            val k = buf.getShort(12).toInt() and 0xFFFF
            val esi = buf.getInt(14).toLong() and MASK32
            val degree = buf.getShort(18).toInt() and 0xFFFF
            if (k == 0 || bytes.size != HEADER + k + 4) return null
            val crc = CRC32().apply { update(bytes, 0, HEADER + k) }.value
            if ((buf.getInt(HEADER + k).toLong() and MASK32) != crc) return null
            return Frame(session, length, k, esi, degree, bytes.copyOfRange(HEADER, HEADER + k))
        }

        fun parse(text: String): Frame? = Base45.decode(text)?.let { parse(it) }
    }
}
