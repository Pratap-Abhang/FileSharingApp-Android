package com.pratap.filesharingapp.codec

/**
 * Peeling decoder for the fountain stream. Feed it scanned QR strings with
 * [addText]; when [isDone] is true, [result] returns the bundle bytes.
 * Not thread-safe: call it from one thread.
 */
class FountainDecoder {
    var session: Long = -1; private set
    var blockCount: Int = 0; private set
    var knownBlocks: Int = 0; private set
    private var length: Long = -1
    private var blockSize: Int = 0
    private var blocks: Array<ByteArray?> = emptyArray()
    private val pending = ArrayList<Pending>()
    private val seen = HashSet<Long>()

    private class Pending(val idx: MutableSet<Int>, val data: ByteArray)

    val isDone: Boolean get() = blockCount > 0 && knownBlocks == blockCount
    val progress: Float get() = if (blockCount == 0) 0f else knownBlocks.toFloat() / blockCount

    /** Returns true if [text] was a valid frame. */
    fun addText(text: String): Boolean = Frame.parse(text)?.also { add(it) } != null

    fun add(frame: Frame) {
        if (frame.session != session || frame.bundleLength != length || frame.blockSize != blockSize) {
            reset(frame)
        }
        if (isDone || !seen.add(frame.esi)) return
        val idx = Fountain.decoderIndices(frame.esi, session, blockCount, frame.degree).toMutableSet()
        addPacket(idx, frame.payload.copyOf())
    }

    fun result(): ByteArray {
        check(isDone) { "not finished" }
        val out = ByteArray(length.toInt())
        for (i in 0 until blockCount) {
            val start = i * blockSize
            val n = minOf(blockSize, out.size - start)
            System.arraycopy(blocks[i]!!, 0, out, start, n)
        }
        return out
    }

    private fun reset(f: Frame) {
        session = f.session
        length = f.bundleLength
        blockSize = f.blockSize
        blockCount = maxOf(1, ((length + blockSize - 1) / blockSize).toInt())
        blocks = arrayOfNulls(blockCount)
        knownBlocks = 0
        pending.clear()
        seen.clear()
    }

    private fun addPacket(idx: MutableSet<Int>, data: ByteArray) {
        val it = idx.iterator()
        while (it.hasNext()) {
            val b = blocks[it.next()]
            if (b != null) { xorInto(data, b); it.remove() }
        }
        if (idx.isEmpty()) return
        if (idx.size > 1) { pending.add(Pending(idx, data)); return }

        val queue = ArrayDeque<Pair<Int, ByteArray>>()
        queue.addLast(idx.first() to data)
        while (queue.isNotEmpty()) {
            val (i, d) = queue.removeLast()
            if (blocks[i] != null) continue
            blocks[i] = d
            knownBlocks++
            val iter = pending.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                if (p.idx.remove(i)) xorInto(p.data, d)
                when (p.idx.size) {
                    0 -> iter.remove()
                    1 -> { queue.addLast(p.idx.first() to p.data); iter.remove() }
                }
            }
        }
    }

    private fun xorInto(a: ByteArray, b: ByteArray) {
        for (j in a.indices) a[j] = (a[j].toInt() xor b[j].toInt()).toByte()
    }
}
