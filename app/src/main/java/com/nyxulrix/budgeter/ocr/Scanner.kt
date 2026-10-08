package com.nyxulrix.budgeter.ocr

import android.content.Context
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

/** On-device text recognition. The image is read on the phone and never uploaded. */
object Ocr {
    /**
     * Recognised text as printed rows: ML Kit lines that sit at the same height are joined left to right.
     * Heights are measured along the text's own slant, so a tilted photo doesn't pull a price onto the row above.
     */
    suspend fun rows(ctx: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(ctx, uri)
        val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await()
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return result.text
        val slant = lines.mapNotNull { l ->
            l.cornerPoints?.takeIf { it.size >= 2 && it[1].x - it[0].x > 20 }?.let { kotlin.math.atan2((it[1].y - it[0].y).toDouble(), (it[1].x - it[0].x).toDouble()) }
        }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val (sin, cos) = kotlin.math.sin(slant) to kotlin.math.cos(slant)
        fun y(l: com.google.mlkit.vision.text.Text.Line) = l.boundingBox!!.let { -it.exactCenterX() * sin + it.exactCenterY() * cos }
        fun x(l: com.google.mlkit.vision.text.Text.Line) = l.boundingBox!!.let { it.exactCenterX() * cos + it.exactCenterY() * sin }
        val heights = lines.map { l ->
            l.cornerPoints?.takeIf { it.size >= 4 }?.let { kotlin.math.hypot((it[3].x - it[0].x).toDouble(), (it[3].y - it[0].y).toDouble()) }
                ?: l.boundingBox!!.height().toDouble()
        }.sorted()
        val tolerance = heights[heights.size / 2] * 0.6
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
        for (l in lines.sortedBy { y(it) }) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(y(row.first()) - y(l)) <= tolerance) row += l
            else rows += mutableListOf(l)
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.boundingBox!!.left }.joinToString("   ") { it.text } }
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
            status = if (provider != null) "Asking ${provider.label} (text only)…" else "Sorting out the receipt…"
            val receipt = try {
                Ai.parse(ctx, provider, text, fallback, st.categories)
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
