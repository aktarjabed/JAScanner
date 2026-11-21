package aktarjabed.com.jaascanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import aktarjabed.com.jaascanner.core.Formatters
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Renders PDF pages to JPEG files, for sharing or saving to the gallery. */
object PageImageExporter {

    private const val EXPORT_WIDTH_PX = 1654 // about A4 at 200 dpi

    suspend fun renderToFiles(
        context: Context,
        pdf: File,
        baseName: String,
        pages: List<Int>,
        quality: Int = 90,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        val renderer = PdfPageRenderer.open(pdf)
            ?: throw PdfToolException("Could not open this PDF.")
        // One private directory per export: exporting the same document twice must never overwrite
        // the first batch's JPEGs.
        val batchDir = File(File(context.cacheDir, "share"), UUID.randomUUID().toString()).apply { mkdirs() }
        val safeName = Formatters.safeFileName(baseName)
        val files = mutableListOf<File>()
        try {
            val unique = LinkedHashSet<Int>()
            for (index in pages) {
                if (index < 0 || index >= renderer.pageCount) {
                    throw PdfToolException("Page ${index + 1} is outside this document (1-${renderer.pageCount}).")
                }
                unique.add(index)
            }
            unique.forEachIndexed { position, index ->
                val bitmap = renderer.render(index, EXPORT_WIDTH_PX)
                    ?: throw PdfToolException("Could not render page ${index + 1}.")
                try {
                    val file = File(batchDir, "$safeName-page-${index + 1}.jpg")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                    files.add(file)
                } finally {
                    bitmap.recycle()
                }
                onProgress(position + 1, unique.size)
            }
            files
        } catch (error: CancellationException) {
            files.forEach { it.delete() }
            batchDir.delete()
            throw error
        } catch (error: Throwable) {
            files.forEach { it.delete() }
            batchDir.delete()
            throw error
        } finally {
            withContext(NonCancellable) { renderer.release() }
        }
    }

    /** Copies the images into Pictures/JAScanner. Needs no storage permission on Android 10+. */
    suspend fun saveToGallery(context: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
        var saved = 0
        for (file in files) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JAScanner")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                saved++
            } catch (error: CancellationException) {
                // Without this, cancelling mid-loop (leaving the screen during a big "save to
                // gallery" job) was caught here on every iteration instead of stopping the loop.
                resolver.delete(uri, null, null)
                throw error
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
            }
        }
        saved
    }
}
