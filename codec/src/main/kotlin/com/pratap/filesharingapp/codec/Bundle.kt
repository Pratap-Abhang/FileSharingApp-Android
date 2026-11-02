package com.pratap.filesharingapp.codec

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

class BundleFile(val name: String, val mime: String, val data: ByteArray)

/** Packs photos into one byte string, see protocol/spec.md section 1. */
object Bundle {
    private val MAGIC = "FSB1".toByteArray(Charsets.US_ASCII)

    fun pack(files: List<BundleFile>): ByteArray {
        val manifest = buildString {
            append("{\"v\":1,\"files\":[")
            files.forEachIndexed { i, f ->
                if (i > 0) append(',')
                append("{\"name\":").append(quote(f.name))
                append(",\"size\":").append(f.data.size)
                append(",\"mime\":").append(quote(f.mime)).append('}')
            }
            append("]}")
        }.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(ByteBuffer.allocate(4).putInt(manifest.size).array())
        out.write(manifest)
        files.forEach { out.write(it.data) }
        out.write(MessageDigest.getInstance("SHA-256").digest(out.toByteArray()))
        return out.toByteArray()
    }

    /** Throws [IllegalArgumentException] if the bundle is corrupt. */
    fun unpack(bundle: ByteArray): List<BundleFile> {
        require(bundle.size >= 40) { "bundle too short" }
        require(bundle.copyOfRange(0, 4).contentEquals(MAGIC)) { "bad magic" }
        val body = bundle.copyOfRange(0, bundle.size - 32)
        val hash = MessageDigest.getInstance("SHA-256").digest(body)
        require(hash.contentEquals(bundle.copyOfRange(bundle.size - 32, bundle.size))) { "hash mismatch" }
        val mlen = ByteBuffer.wrap(bundle, 4, 4).int
        val manifest = MiniJson.parse(String(bundle, 8, mlen, Charsets.UTF_8)) as Map<*, *>
        var pos = 8 + mlen
        return (manifest["files"] as List<*>).map { entry ->
            val f = entry as Map<*, *>
            val size = (f["size"] as Number).toInt()
            require(pos + size <= body.size) { "file overruns bundle" }
            BundleFile(f["name"] as String, f["mime"] as String, bundle.copyOfRange(pos, pos + size)).also { pos += size }
        }
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}

/** Just enough JSON to read the manifest (objects, arrays, strings, numbers, true/false/null). */
internal object MiniJson {
    fun parse(s: String): Any? = Parser(s).run { value().also { ws(); require(i == s.length) { "trailing data" } } }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }
        fun lit(word: String, v: Any?): Any? { require(s.startsWith(word, i)); i += word.length; return v }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); require(s[i] == ':'); i++
                m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                require(s[i] == '}'); i++; return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value()); ws()
                if (s[i] == ',') { i++; continue }
                require(s[i] == ']'); i++; return l
            }
        }
        fun str(): String {
            require(s[i] == '"'); i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
    }
}
