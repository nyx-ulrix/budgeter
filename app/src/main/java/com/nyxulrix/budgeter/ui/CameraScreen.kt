package com.nyxulrix.budgeter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * In-app receipt camera, framed like the rest of the app. The photo is saved to the scanner's private file,
 * read on the phone, then deleted.
 */
@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scanner = LocalScanner.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                provider = future.get().also { p ->
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    runCatching {
                        p.unbindAll()
                        p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }.onFailure { Toast.makeText(ctx, "Camera unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }

    fun snap() {
        if (busy) return
        busy = true
        capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(scanner.photoFile).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    nav.back()
                    scanner.readPhoto()
                }
                override fun onError(e: ImageCaptureException) {
                    busy = false
                    Toast.makeText(ctx, "Couldn't take the photo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    Page {
        PageHeader("Scan")
        Window("Camera.exe") {
            if (granted) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).frame(Px.navy).semantics { contentDescription = "Camera preview" }) {
                    AndroidView({ previewView }, Modifier.fillMaxSize())
                    ReceiptGuides()
                }
                Small("Lay the receipt flat and fit it inside the corners. The photo is read (by your AI provider if you've added one), then deleted from the phone.")
            } else {
                Body("Budgeter needs the camera to read receipts.")
                if (asked) Small("If you chose \"Don't allow\", turn the camera on for Budgeter in Android settings, or import a screenshot instead.")
                PixelButton("Allow camera", { ask.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
            }
        }
        if (granted) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton(if (flash) "Flash on" else "Flash off", { flash = !flash }, kind = Kind.SECONDARY)
            PixelButton(if (busy) "Saving…" else "Snap", { snap() }, Modifier.weight(1f).heightIn(min = 64.dp), glyph = Glyphs.camera, enabled = !busy)
        }
        PixelButton("Use a screenshot instead", { nav.back(); scanner.screenshot() }, Modifier.fillMaxWidth(), kind = Kind.SECONDARY, glyph = Glyphs.image)
    }
}

/** Orange pixel corner brackets showing where to put the receipt. */
@Composable
private fun ReceiptGuides() {
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        val t = 4.dp.toPx(); val len = 28.dp.toPx(); val w = size.width; val h = size.height
        val c = Px.orange
        listOf(Offset(0f, 0f) to (1 to 1), Offset(w, 0f) to (-1 to 1), Offset(0f, h) to (1 to -1), Offset(w, h) to (-1 to -1)).forEach { (o, d) ->
            val (dx, dy) = d
            drawRect(c, Offset(if (dx > 0) o.x else o.x - len, if (dy > 0) o.y else o.y - t), Size(len, t))
            drawRect(c, Offset(if (dx > 0) o.x else o.x - t, if (dy > 0) o.y else o.y - len), Size(t, len))
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.padding(8.dp).background(Px.brown.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Label("Receipt goes here", color = Px.creamLight)
        }
    }
}
