package com.pratap.filesharingapp.receive

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import com.pratap.filesharingapp.codec.BundleFile

/**
 * Saves received photos into the gallery under Pictures/FileSharingApp.
 *
 * Uses MediaStore with IS_PENDING so the gallery never shows a half-written file. On Android 10+
 * an app may add its own files this way without any storage permission, which is why the app
 * needs no storage permissions at all.
 */
class PhotoSaver(private val resolver: ContentResolver) {

    /** Saves every file and returns how many were written. Throws on the first failure. */
    fun saveAll(files: List<BundleFile>): Int {
        files.forEach { save(it) }
        return files.size
    }

    private fun save(file: BundleFile) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, file.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FileSharingApp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create ${file.name} in the gallery")
        try {
            resolver.openOutputStream(uri)?.use { it.write(file.data) } ?: error("Could not open ${file.name}")
            // Clearing IS_PENDING publishes the photo to the gallery.
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            // Remove the empty entry so a failed save does not leave a broken photo behind.
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
