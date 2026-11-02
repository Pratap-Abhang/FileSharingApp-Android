package com.pratap.filesharingapp.send

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Send flow: pick photos, wait for them to be packed, then show the looping QR codes.
 * While the codes are on screen the display is kept awake at full brightness, because a dim
 * or sleeping screen is the most common reason a scan fails.
 */
@Composable
fun SendScreen(onBack: () -> Unit, vm: SendViewModel = viewModel()) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PHOTOS)
    ) { uris -> vm.start(uris) }

    when (val s = vm.state) {
        SendState.Idle -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Send photos", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Pick up to $MAX_PHOTOS photos. They are shrunk first, so each one takes about 10 seconds to send.",
                Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Choose photos") }
            TextButton(onClick = onBack) { Text("Back") }
        }

        is SendState.Preparing -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text("Preparing photo ${s.done + 1} of ${s.total}", Modifier.padding(top = 16.dp))
        }

        is SendState.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, textAlign = TextAlign.Center)
            Button(onClick = { vm.reset() }, Modifier.padding(top = 16.dp)) { Text("Try again") }
            TextButton(onClick = onBack) { Text("Back") }
        }

        is SendState.Streaming -> StreamingContent(vm, s, onStop = { vm.reset(); onBack() })
    }
}

@Composable
private fun StreamingContent(vm: SendViewModel, s: SendState.Streaming, onStop: () -> Unit) {
    KeepScreenBrightAndOn()
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${s.photoCount} photo(s), about ${s.estimatedSeconds} s. Point the other phone's camera at this screen.",
            color = Color.Black,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            vm.currentFrame?.let { bitmap ->
                // FilterQuality.None keeps module edges sharp when the 1 px/module bitmap is scaled up.
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code carrying the photos",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } ?: CircularProgressIndicator()
        }
        Text("Speed: ${"%.0f".format(vm.fps)} frames/s", color = Color.Black)
        Slider(
            value = vm.fps,
            onValueChange = vm::updateFps,
            valueRange = SendViewModel.MIN_FPS..SendViewModel.MAX_FPS,
        )
        Text(
            "If the other phone struggles, slide left to slow down.",
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onStop) { Text("Stop") }
    }
}

/** Forces maximum brightness and prevents the screen from sleeping while this is on screen. */
@Composable
private fun KeepScreenBrightAndOn() {
    val window = (LocalContext.current as Activity).window
    DisposableEffect(window) {
        val previous = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}

/** More photos than this would take minutes and risk running out of memory while packing. */
private const val MAX_PHOTOS = 20
