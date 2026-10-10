package com.nyxulrix.budgeter.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Base64
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nyxulrix.budgeter.ai.Ai
import com.nyxulrix.budgeter.core.ParsedReceipt
import com.nyxulrix.budgeter.core.ReceiptText
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.activeTrip
import com.nyxulrix.budgeter.data.currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/** On-device text recognition, used by the built-in reader and as the AI's fallback. */
object Ocr {
    /** Recognised text as printed rows (see [ReceiptText.joinRows]). */
    suspend fun rows(ctx: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(ctx, uri)
        val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await()
        val segs = result.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val c = l.cornerPoints?.takeIf { it.size >= 4 } ?: return@mapNotNull null
            ReceiptText.Seg(
                l.text, c.sumOf { it.x } / 4.0, c.sumOf { it.y } / 4.0,
                w = kotlin.math.hypot((c[1].x - c[0].x).toDouble(), (c[1].y - c[0].y).toDouble()),
                h = kotlin.math.hypot((c[3].x - c[0].x).toDouble(), (c[3].y - c[0].y).toDouble()),
                angle = kotlin.math.atan2((c[1].y - c[0].y).toDouble(), (c[1].x - c[0].x).toDouble()),
            )
        }
        return if (segs.isEmpty()) result.text else ReceiptText.joinRows(segs)
    }
}

/** Starts a camera or screenshot scan and turns it into a [ParsedReceipt]. */
class Scanner(private val ctx: Context, private val scope: CoroutineScope, private val state: () -> AppState, private val onResult: (ParsedReceipt, String) -> Unit) {
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    internal lateinit var pickLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>
    /** Where the in-app camera saves the shot. Private to the app; deleted after reading. */
    val photoFile = File(File(ctx.cacheDir, "receipts").apply { mkdirs() }, "receipt.jpg")
    private val photoUri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", photoFile)

    /** Opens the in-app camera screen (set by the app shell, which owns navigation). */
    var openCamera: () -> Unit = {}
    fun camera() = openCamera()
    fun readPhoto() = read(photoUri)
    fun screenshot() = pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    internal fun read(uri: Uri) = scope.launch {
        busy = true
        try {
            status = "Reading text on your phone…"
            val text = runCatching { Ocr.rows(ctx, uri) }.getOrElse { toast("Couldn't read that image."); return@launch }
            if (text.isBlank()) { toast("No text found. Try a sharper, flatter photo."); return@launch }
            val st = state()
            val fallback = st.activeTrip()?.currency ?: st.currency
            val provider = Ai.active(ctx)
            status = if (provider != null) "Sending the photo to ${provider.label}…" else "Sorting out the receipt…"
            // With an AI provider the photo itself goes to it. A model that can't read images falls back to the
            // phone's text, then to the built-in reader.
            val receipt = try {
                if (provider == null) ReceiptText.parse(text, fallback)
                else runCatching { Ai.parse(ctx, provider, text, fallback, st.categories, photo(uri)) }
                    .getOrElse { Ai.parse(ctx, provider, text, fallback, st.categories) }
            } catch (e: Exception) {
                toast((e.message ?: "AI failed.") + " Used the built-in reader instead.")
                ReceiptText.parse(text, fallback)
            }
            onResult(receipt, text)
        } finally {
            photoFile.delete()
            busy = false
        }
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    /** The photo as a JPEG data URL, longest side at most 1600 px: enough to read, small to send. */
    private fun photo(uri: Uri): String {
        val max = 1600
        val bmp = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            val s = max.toDouble() / maxOf(info.size.width, info.size.height)
            if (s < 1) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > max) sample *= 2
            ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: throw Exception("Couldn't open the photo.")
        }
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

@Composable
fun rememberScanner(st: AppState, onResult: (ParsedReceipt, String) -> Unit): Scanner {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(st)
    val callback by rememberUpdatedState(onResult)
    val scanner = remember { Scanner(ctx, scope, { current }, { r, t -> callback(r, t) }) }
    scanner.pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { scanner.read(it) } }
    return scanner
}
