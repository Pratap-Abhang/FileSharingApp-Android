package com.pratap.filesharingapp.receive

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratap.filesharingapp.codec.Bundle
import com.pratap.filesharingapp.codec.FountainDecoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the receive screen is showing. */
sealed interface ReceiveState {
    /** Camera is open, waiting for or collecting frames. */
    data object Scanning : ReceiveState

    /** All frames collected; unpacking and saving. */
    data object Saving : ReceiveState

    data class Done(val photoCount: Int) : ReceiveState

    data class Failed(val message: String) : ReceiveState
}

/**
 * Collects scanned QR text into a [FountainDecoder] and saves the photos once it finishes.
 *
 * The camera delivers frames on its own thread, but [FountainDecoder] is not thread-safe, so
 * every decoder call is funnelled through one single-threaded dispatcher.
 */
class ReceiveViewModel(app: Application) : AndroidViewModel(app) {
    var state: ReceiveState by mutableStateOf(ReceiveState.Scanning)
        private set

    /** 0..1 share of the photo data collected so far. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** Number of valid frames received, to show the user the scan is working. */
    var framesSeen by mutableIntStateOf(0)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    private var decoder = FountainDecoder()
    private var lastText: String? = null

    /**
     * Called by the camera analyzer for every QR code it reads, up to 30 times a second.
     * Safe to call from any thread.
     */
    fun onQrText(text: String) {
        viewModelScope.launch(decodeDispatcher) {
            if (state != ReceiveState.Scanning) return@launch
            // The sender's frame stays on screen for several camera frames; skip exact repeats cheaply.
            if (text == lastText) return@launch
            lastText = text
            if (!decoder.addText(text)) return@launch // not ours, or damaged: the fountain code covers for it
            progress = decoder.progress
            framesSeen++
            if (decoder.isDone) finish()
        }
    }

    /** Unpacks the finished bundle and writes the photos to the gallery. */
    private suspend fun finish() {
        state = ReceiveState.Saving
        state = try {
            val files = Bundle.unpack(decoder.result())
            val saved = withContext(Dispatchers.IO) {
                PhotoSaver(getApplication<Application>().contentResolver).saveAll(files)
            }
            ReceiveState.Done(saved)
        } catch (e: IllegalArgumentException) {
            // SHA-256 mismatch or malformed bundle: nothing was saved. Start over.
            ReceiveState.Failed("The photos arrived damaged. Please try again.")
        } catch (e: Exception) {
            ReceiveState.Failed(e.message ?: "Could not save the photos.")
        }
    }

    /** Discards everything collected so far and returns to scanning. */
    fun reset() {
        viewModelScope.launch(decodeDispatcher) {
            decoder = FountainDecoder()
            lastText = null
            progress = 0f
            framesSeen = 0
            state = ReceiveState.Scanning
        }
    }
}
