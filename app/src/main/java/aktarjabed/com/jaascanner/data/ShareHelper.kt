package aktarjabed.com.jaascanner.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import aktarjabed.com.jaascanner.core.Formatters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies files into the cache "share" folder and hands them to the system share sheet. */
object ShareHelper {

    private fun shareDir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun stageCopy(context: Context, source: File, displayName: String, extension: String): Uri =
        withContext(Dispatchers.IO) {
            val target = File(shareDir(context), Formatters.safeFileName(displayName) + "." + extension)
            source.copyTo(target, overwrite = true)
            uriFor(context, target)
        }

    suspend fun sharePdf(context: Context, source: File, displayName: String) {
        val uri = stageCopy(context, source, displayName, "pdf")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun shareImages(context: Context, files: List<File>) {
        val uris = withContext(Dispatchers.IO) {
            files.map { file ->
                val target = File(shareDir(context), file.name)
                file.copyTo(target, overwrite = true)
                uriFor(context, target)
            }
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share images").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Returns false when the device has no app that can receive plain text. */
    fun shareText(context: Context, text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(send, "Share text").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (error: ActivityNotFoundException) {
            false
        }
    }

    /** Shares any number of PDFs in one chooser; a single document uses the normal single-share path. */
    suspend fun sharePdfs(context: Context, documents: List<Pair<File, String>>) {
        if (documents.isEmpty()) return
        if (documents.size == 1) {
            sharePdf(context, documents.first().first, documents.first().second)
            return
        }
        val uris = withContext(Dispatchers.IO) {
            documents.mapIndexed { index, (file, name) ->
                // An index keeps two documents with the same display name from overwriting
                // each other's staged copy in the shared cache directory.
                val target = File(shareDir(context), Formatters.safeFileName(name) + "-$index.pdf")
                file.copyTo(target, overwrite = true)
                uriFor(context, target)
            }
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share PDFs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun sharePng(context: Context, source: File, displayName: String) {
        val uri = stageCopy(context, source, displayName, "png")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
