package com.pratap.filesharingapp.codec

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the Kotlin codec against protocol/test-vectors/vectors.json (made by protocol/tools/reference.py). */
class CodecTest {
    private val v: JsonObject = JsonParser.parseString(
        File(System.getProperty("vectors.path") ?: "../../protocol/test-vectors/vectors.json").readText()
    ).asJsonObject

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun detBytes(seed: Long, n: Int): ByteArray { val r = XorShift32(seed); return ByteArray(n) { r.next().toByte() } }

    @Test fun base45() {
        for (c in v["base45"].asJsonArray) {
            val o = c.asJsonObject
            assertEquals(o["text"].asString, Base45.encode(hex(o["hex"].asString)))
            assertEquals(o["hex"].asString, Base45.decode(o["text"].asString)!!.hex())
        }
        for (bad in v["base45Invalid"].asJsonArray) assertNull(Base45.decode(bad.asString), bad.asString)
    }

    @Test fun prngAndSeeds() {
        val r = XorShift32(1)
        for (x in v["xorshift32"].asJsonObject["first5"].asJsonArray) assertEquals(x.asLong, r.next())
        for (c in v["packetSeed"].asJsonArray) {
            val o = c.asJsonObject
            assertEquals(o["seed"].asLong, Fountain.packetSeed(o["esi"].asLong, o["session"].asLong))
        }
        val cdf = Fountain.solitonCdf(20)
        v["solitonCdfN20First5"].asJsonArray.forEachIndexed { i, x -> assertEquals(x.asDouble, cdf[i], 1e-15) }
    }

    @Test fun bundleMatchesReference() {
        val b = v["bundle"].asJsonObject
        val files = b["files"].asJsonArray.map {
            val o = it.asJsonObject
            BundleFile(o["name"].asString, o["mime"].asString, detBytes(o["dataSeed"].asLong, o["size"].asInt))
        }
        val packed = Bundle.pack(files)
        assertEquals(b["hex"].asString, packed.hex())
        val back = Bundle.unpack(packed)
        assertEquals(files.map { it.name }, back.map { it.name })
        files.zip(back).forEach { (a, z) -> assertContentEquals(a.data, z.data) }
    }

    @Test fun framesMatchReference() {
        val e = v["encoder"].asJsonObject
        val bundle = hex(v["bundle"].asJsonObject["hex"].asString)
        val enc = FountainEncoder(bundle, e["session"].asLong, e["blockSize"].asInt)
        assertEquals(e["n"].asInt, enc.blockCount)
        for (f in e["frames"].asJsonArray) {
            val o = f.asJsonObject
            val esi = o["esi"].asLong
            assertEquals(o["indices"].asJsonArray.map { it.asInt }, enc.indices(esi).toList(), "indices esi=$esi")
            val frame = enc.frame(esi)
            assertEquals(o["frameHex"].asString, frame.toBytes().hex(), "frame esi=$esi")
            assertEquals(o["base45"].asString, frame.toText())
            val parsed = assertNotNull(Frame.parse(o["base45"].asString))
            assertEquals(o["degree"].asInt, parsed.degree)
        }
    }

    @Test fun corruptFramesAreRejected() {
        val bytes = FountainEncoder(ByteArray(3000) { it.toByte() }, 7L, 500).frame(9).toBytes()
        bytes[30] = (bytes[30].toInt() xor 1).toByte()
        assertNull(Frame.parse(bytes))
        assertNull(Frame.parse("not a frame"))
    }

    @Test fun decodesWithLossAndLateStart() {
        val rnd = Random(42)
        for (size in listOf(1, 999, 1000, 1001, 25_000, 200_000)) {
            val bundle = Bundle.pack(listOf(BundleFile("x.bin", "application/octet-stream", rnd.nextBytes(size))))
            val enc = FountainEncoder(bundle, rnd.nextLong() and MASK32)
            val dec = FountainDecoder()
            var esi = rnd.nextLong(0, 3L * enc.blockCount + 1)
            var used = 0
            while (!dec.isDone) {
                if (rnd.nextDouble() < 0.3) { esi++; continue }
                assertTrue(dec.addText(enc.frame(esi++).toText()))
                used++
                assertTrue(used < enc.blockCount * 4 + 50, "decoder stalled at size=$size")
            }
            assertContentEquals(bundle, dec.result())
            assertEquals("x.bin", Bundle.unpack(dec.result()).single().name)
        }
    }

    @Test fun newSessionResetsDecoder() {
        val a = FountainEncoder(ByteArray(5000) { 1 }, 1L)
        val b = FountainEncoder(ByteArray(2500) { 2 }, 2L)
        val dec = FountainDecoder()
        dec.add(a.frame(0)); dec.add(a.frame(1))
        for (i in 0L until b.blockCount) dec.add(b.frame(i))
        assertTrue(dec.isDone)
        assertContentEquals(ByteArray(2500) { 2 }, dec.result())
    }
}
