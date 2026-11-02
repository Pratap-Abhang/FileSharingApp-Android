package com.pratap.filesharingapp.codec

/** Turns a bundle into an endless stream of frames. Call [frame] with esi = 0, 1, 2, ... */
class FountainEncoder(
    private val bundle: ByteArray,
    val session: Long,
    val blockSize: Int = DEFAULT_BLOCK_SIZE,
) {
    val blockCount: Int = maxOf(1, (bundle.size + blockSize - 1) / blockSize)
    private val cdf = Fountain.solitonCdf(blockCount)

    fun indices(esi: Long): IntArray = Fountain.encoderIndices(esi, session, blockCount, cdf)

    fun frame(esi: Long): Frame {
        val idx = indices(esi)
        val payload = ByteArray(blockSize)
        for (b in idx) {
            val start = b * blockSize
            val end = minOf(start + blockSize, bundle.size)
            for (j in start until end) payload[j - start] = (payload[j - start].toInt() xor bundle[j].toInt()).toByte()
        }
        return Frame(session, bundle.size.toLong(), blockSize, esi, idx.size, payload)
    }

    companion object {
        const val DEFAULT_BLOCK_SIZE = 1000
    }
}
