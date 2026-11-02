package com.pratap.filesharingapp.send

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratap.filesharingapp.codec.Bundle
import com.pratap.filesharingapp.codec.FountainEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** What the send screen is showing. */
sealed interface SendState {
    /** Nothing picked yet. */
    data object Idle : SendState

    /** Photos are being compressed and packed. */
    data class Preparing(val done: Int, val total: Int) : SendState

    /** The QR loop is running. */
    data class Streaming(
        val photoCount: Int,
        val bundleBytes: Int,
        val blockCount: Int,
        /** Rough time for the receiver to collect enough frames, in seconds. */
        val estimatedSeconds: Int,
    ) : SendState

    data class Failed(val message: String) : SendState
}

/**
 * Prepares the selected photos and runs the QR loop.
 *
 * The loop is endless on purpose: the receiver can start at any time and the fountain code
 * means any sufficiently large set of frames is enough. The user stops it with the back button.
 */
class SendViewModel(app: Application) : AndroidViewModel(app) {
    var state: SendState by mutableStateOf(SendState.Idle)
        private set

    /** The QR currently on screen. Compose reads this on every frame change. */
    var currentFrame: android.graphics.Bitmap? by mutableStateOf(null)
        private set

    /** Frames per second shown, adjustable while streaming. */
    var fps by mutableFloatStateOf(DEFAULT_FPS)
        private set

    private var job: Job? = null

    fun updateFps(value: Float) {
        fps = value.coerceIn(MIN_FPS, MAX_FPS)
    }

    /** Compresses [uris], packs them and starts looping QR frames. */
    fun start(uris: List<Uri>) {
        if (uris.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                val compressor = ImageCompressor(getApplication<Application>().contentResolver)
                val files = ArrayList<com.pratap.filesharingapp.codec.BundleFile>()
                uris.forEachIndexed { i, uri ->
                    state = SendState.Preparing(done = i, total = uris.size)
                    // A photo that cannot be decoded is skipped rather than failing the whole batch.
                    runCatching { compressor.compress(uri) }.onSuccess { files.add(it) }
                }
                if (files.isEmpty()) {
                    state = SendState.Failed("None of the selected photos could be read.")
                    return@launch
                }
                val bundle = Bundle.pack(files)
                val session = SecureRandom().nextInt().toLong() and 0xFFFFFFFFL
                val encoder = FountainEncoder(bundle, session)
                // Receivers need about 20 to 30% more frames than blocks, so budget 1.3x.
                val seconds = (encoder.blockCount * 1.3f / fps).toInt() + 1
                state = SendState.Streaming(files.size, bundle.size, encoder.blockCount, seconds)
                streamLoop(encoder)
            } catch (e: OutOfMemoryError) {
                state = SendState.Failed("Too many photos at once. Try a smaller batch.")
            } catch (e: Exception) {
                state = SendState.Failed(e.message ?: "Could not prepare the photos.")
            }
        }
    }

    /** Shows frame after frame at the chosen rate until the coroutine is cancelled. */
    private suspend fun streamLoop(encoder: FountainEncoder) {
        var esi = 0L
        while (currentCoroutineContext().isActive) {
            val started = System.nanoTime()
            val bitmap = QrRenderer.render(encoder.frame(esi).toText())
            withContext(Dispatchers.Main) { currentFrame = bitmap }
            esi++
            // Sleep only for what is left of this frame's time slot so the rate stays steady
            // even when rendering takes a few milliseconds.
            val slotMs = (1000f / fps).toLong()
            val usedMs = (System.nanoTime() - started) / 1_000_000
            delay((slotMs - usedMs).coerceAtLeast(1))
        }
    }

    fun reset() {
        job?.cancel()
        currentFrame = null
        state = SendState.Idle
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        const val MIN_FPS = 4f
        const val MAX_FPS = 15f
        const val DEFAULT_FPS = 10f
    }
}
