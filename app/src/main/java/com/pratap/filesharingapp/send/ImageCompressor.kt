package com.pratap.filesharingapp.send

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.pratap.filesharingapp.codec.BundleFile
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a photo so it is quick to send through the screen.
 *
 * A 12 MP phone photo is 3 to 5 MB, which would take minutes to show as QR codes. Resizing the
 * long edge to [maxEdge] pixels and re-encoding as lossy WebP gives roughly 100 to 300 KB with
 * no visible loss on a phone screen.
 */
class ImageCompressor(
    private val resolver: ContentResolver,
    private val maxEdge: Int = DEFAULT_MAX_EDGE,
    private val quality: Int = DEFAULT_QUALITY,
) {
    /**
     * Decodes [uri], scales it down and returns it as a [BundleFile] ready for the bundle.
     * ImageDecoder applies the EXIF rotation for us, so the saved photo is the right way up.
     * Throws if the image cannot be decoded; callers should skip that photo.
     */
    fun compress(uri: Uri): BundleFile {
        val source = ImageDecoder.createSource(resolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            val scale = if (longEdge > maxEdge) maxEdge.toFloat() / longEdge else 1f
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
            // Software bitmaps can be compressed; hardware bitmaps cannot.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        try {
            check(bitmap.compress(webpFormat(), quality, out)) { "WebP encoding failed" }
        } finally {
            bitmap.recycle()
        }
        return BundleFile(name = "${baseName(uri)}.webp", mime = "image/webp", data = out.toByteArray())
    }

    /** The picked file's display name without its extension, or a generic fallback. */
    private fun baseName(uri: Uri): String {
        val display = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val name = display?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "photo"
        // Keep names filesystem-safe on the receiving phone.
        return name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60)
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP

    companion object {
        const val DEFAULT_MAX_EDGE = 1600
        const val DEFAULT_QUALITY = 70
    }
}
