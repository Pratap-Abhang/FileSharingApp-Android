package com.pratap.filesharingapp.send

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Turns frame text into a QR bitmap.
 *
 * The text is Base45, which only uses QR "alphanumeric" characters. ZXing notices that and picks
 * alphanumeric mode, which is what makes a 1024-byte frame fit in a version-25 code.
 * The bitmap is one pixel per module (plus the quiet zone); the UI scales it up with no
 * smoothing, which keeps the edges sharp for the other phone's camera.
 */
object QrRenderer {
    /** Quiet zone in modules; the QR spec asks for 4. */
    private const val QUIET_ZONE = 4

    fun render(text: String): Bitmap {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "ISO-8859-1")
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.L, hints).matrix
        val size = matrix.width + 2 * QUIET_ZONE
        val pixels = IntArray(size * size) { Color.WHITE }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y).toInt() == 1) pixels[(y + QUIET_ZONE) * size + (x + QUIET_ZONE)] = Color.BLACK
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
