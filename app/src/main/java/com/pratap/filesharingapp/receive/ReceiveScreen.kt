package com.pratap.filesharingapp.receive

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors

/** Receive flow: ask for the camera, scan the sender's screen, show progress, save the photos. */
@Composable
fun ReceiveScreen(onBack: () -> Unit, vm: ReceiveViewModel = viewModel()) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    LaunchedEffect(Unit) { if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA) }

    when (val s = vm.state) {
        ReceiveState.Scanning ->
            if (hasCamera) ScanningContent(vm, onBack)
            else CenteredColumn {
                Text("The camera is needed to read the other phone's screen.", textAlign = TextAlign.Center)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 16.dp)) {
                    Text("Allow camera")
                }
                TextButton(onClick = onBack) { Text("Back") }
            }

        ReceiveState.Saving -> CenteredColumn {
            CircularProgressIndicator()
            Text("Saving photos…", Modifier.padding(top = 16.dp))
        }

        is ReceiveState.Done -> CenteredColumn {
            Text("Received ${s.photoCount} photo(s)", style = MaterialTheme.typography.headlineMedium)
            Text("They are in your gallery, in the FileSharingApp album.", Modifier.padding(vertical = 12.dp))
            Button(onClick = { vm.reset() }) { Text("Receive more") }
            TextButton(onClick = onBack) { Text("Done") }
        }

        is ReceiveState.Failed -> CenteredColumn {
            Text(s.message, textAlign = TextAlign.Center)
            Button(onClick = { vm.reset() }, Modifier.padding(top = 16.dp)) { Text("Try again") }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun ScanningContent(vm: ReceiveViewModel, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CameraPreview(onText = vm::onQrText, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (vm.framesSeen == 0) "Point the camera at the other phone's QR code and hold steady."
                else "Receiving… ${(vm.progress * 100).toInt()}%",
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onBack) { Text("Cancel") }
        }
    }
}

/** Camera preview plus a QR analyzer, bound to the screen's lifecycle. */
@Composable
private fun CameraPreview(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    // One background thread for the analyzer so scanning never blocks the UI.
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { QrAnalyzer(onText) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            // A dense QR (about 117 modules wide) needs real resolution to resolve each module.
            val selector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
            analyzer.close()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
