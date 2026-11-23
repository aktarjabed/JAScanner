package aktarjabed.com.jaascanner.ui.camera

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.CapturedPage
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.core.ScanSession
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.ImageFilter
import aktarjabed.com.jaascanner.pdf.PageSizeOption
import aktarjabed.com.jaascanner.pdf.PdfTools
import aktarjabed.com.jaascanner.pdf.toUserMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Captures pages with the phone's own camera and turns them into a document, for phones where
 * Google's document scanner is missing or out of date. The camera itself lives in the screen;
 * this holds the page list so a rotation or a trip to another app does not lose captured pages.
 */
class CameraScanViewModel(private val container: AppContainer) : ViewModel() {

    private val session = ScanSession(MAX_PAGES)

    var pageCount by mutableIntStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set
    var progress by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    /** Adds a captured file; a file that does not fit is deleted and false is returned. */
    fun add(file: File): Boolean {
        val added = session.add(CapturedPage(file))
        if (!added) file.delete()
        pageCount = session.count
        return added
    }

    fun removeLast(): Boolean {
        val removed = session.removeLast() ?: return false
        removed.file.delete()
        pageCount = session.count
        return true
    }

    fun discard() {
        session.clear()
        pageCount = 0
    }

    fun save(context: Context, onSaved: (Long) -> Unit) {
        if (saving) return
        val pages = session.snapshot()
        if (pages.isEmpty()) {
            container.messenger.post("Take at least one picture first.")
            return
        }
        val appContext = context.applicationContext
        saving = true
        progress = 0
        total = pages.size
        container.appScope.launch {
            val target = container.documents.newOutputFile()
            try {
                PdfTools.imagesToPdf(
                    context = appContext,
                    uris = pages.map { it.file.toUri() },
                    option = PageSizeOption.Fit,
                    target = target,
                    filter = ImageFilter.Enhanced,
                ) { done, _ -> progress = done }
                val folderId = container.settings.settings.first().defaultFolderId
                    ?.takeIf { container.documents.folderExists(it) }
                val name = Formatters.defaultScanName(System.currentTimeMillis())
                val saved = container.documents.registerOutput(target, name, DocumentEntity.SOURCE_SCAN, folderId)
                if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                session.clear()
                pageCount = 0
                withContext(Dispatchers.Main) { onSaved(saved.id) }
            } catch (error: CancellationException) {
                target.delete()
                session.clear()
                pageCount = 0
                throw error
            } catch (error: Throwable) {
                target.delete()
                session.clear()
                pageCount = 0
                container.messenger.post(error.toUserMessage())
            } finally {
                saving = false
            }
        }
    }

    override fun onCleared() {
        // Pictures that were never saved should not linger in the cache. A save still in flight
        // owns its pictures: it deletes them if it fails and consumes them if it succeeds.
        if (saving) return
        session.clear()
    }

    companion object {
        /** Enough for a long document while keeping the cache folder bounded. */
        const val MAX_PAGES = 50
    }
}
