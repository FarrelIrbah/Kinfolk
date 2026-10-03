package id.kinfolk.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintManager
import java.io.FileOutputStream
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun rememberShare(): (text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
    }
}

@Composable
actual fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        val was = bars.isAppearanceLightStatusBars
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = was }
    }
}

@Composable
actual fun rememberKept(key: String): Kept {
    val prefs = LocalContext.current.getSharedPreferences("kinfolk", Context.MODE_PRIVATE)
    return remember(prefs) { Kept({ prefs.getString(key, null) }, { v -> prefs.edit().apply { if (v == null) remove(key) else putString(key, v) }.apply() }) }
}

private val types = mapOf("application/pdf" to "PDF", "image/jpeg" to "JPG", "image/png" to "PNG")

/** Shared with other apps through the FileProvider in androidApp's manifest (res/xml/file_paths.xml). */
private fun Context.shared(file: File) = FileProvider.getUriForFile(this, "$packageName.files", file)

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val photo = remember(context) { File(context.cacheDir, "camera.jpg") }
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = r.data?.data
        // The camera answers without a uri, having written the photo.
        if (uri == null) { if (photo.length() > 0) picked(PickedFile("", "JPG", photo.readBytes())); return@rememberLauncherForActivityResult }
        val resolver = context.contentResolver
        val ext = types[resolver.getType(uri)] ?: return@rememberLauncherForActivityResult
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }.orEmpty().substringBeforeLast('.')
        // ponytail: read on the main thread; the bucket takes 20 MB at most.
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        picked(PickedFile(name, ext, bytes, if (ext == "PDF") pdfPages(context, bytes) else 1))
    }
    return remember(context) {
        {
            photo.delete()
            val out = context.shared(photo)
            val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, out)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("", out) }
            val file = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, types.keys.toTypedArray())
            launcher.launch(Intent.createChooser(file, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera)))
        }
    }
}

/** Pages in a PDF, for Export's count; 1 when it can't be read (a password, a broken file). */
private fun pdfPages(context: Context, bytes: ByteArray): Int = try {
    val file = File(context.cacheDir, "count.pdf").apply { writeBytes(bytes) }
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { r -> r.pageCount } }.coerceAtLeast(1)
} catch (_: Exception) { 1 }

@Composable
actual fun rememberFileViewer(): (name: String, ext: String, bytes: ByteArray) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { name, ext, bytes ->
            val file = File(context.cacheDir, "docs/${name.ifBlank { "dokumen" }.replace(Regex("""[^\w .-]"""), "_")}.${ext.lowercase()}")
            file.parentFile!!.mkdirs()
            file.writeBytes(bytes)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(context.shared(file), types.entries.first { it.value == ext }.key)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { context.startActivity(view) } catch (_: ActivityNotFoundException) {} // no viewer installed: nothing opens
        }
    }
}

@Composable
actual fun rememberPrinter(): (name: String, pdf: ByteArray, onSent: () -> Unit) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { name, pdf, onSent ->
            var job: PrintJob? = null
            job = (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(name, object : PrintDocumentAdapter() {
                override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, done: LayoutResultCallback, extras: Bundle?) {
                    if (cancel.isCanceled) done.onLayoutCancelled()
                    else done.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), old != new)
                }

                override fun onWrite(pages: Array<PageRange>, out: ParcelFileDescriptor, cancel: CancellationSignal, done: WriteResultCallback) {
                    FileOutputStream(out.fileDescriptor).use { it.write(pdf) }
                    done.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }

                // Leaving the dialog cancels the job; printing (or saving as PDF) queues it, after this returns.
                // ponytail: checked once a second later; a slow spooler that hasn't queued it by then shows no toast.
                override fun onFinish() {
                    Handler(Looper.getMainLooper()).postDelayed({ if (job?.let { it.isQueued || it.isStarted || it.isCompleted } == true) onSent() }, 1000)
                }
            }, null)
        }
    }
}
