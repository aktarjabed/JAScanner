package aktarjabed.com.jaascanner.ocr

import aktarjabed.com.jaascanner.core.Messenger
import aktarjabed.com.jaascanner.data.DocumentRepository
import aktarjabed.com.jaascanner.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Extracts text from a document and stores it so the library search can find it. */
class OcrIndexer(
    private val repository: DocumentRepository,
    private val engine: OcrEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val messenger: Messenger,
) {
    fun indexInBackground(documentId: Long) {
        scope.launch { index(documentId, announce = false) }
    }

    /** Returns true if any text was found and stored. */
    suspend fun index(documentId: Long, announce: Boolean): Boolean {
        val document = repository.get(documentId) ?: return false
        val language = settings.settings.first().ocrLanguage
        val texts = try {
            engine.recognizePdf(repository.fileOf(document), language, MAX_PAGES)
        } catch (error: Exception) {
            if (announce) messenger.post("Text recognition failed on this device.")
            return false
        }
        val text = texts.joinToString("\n\n").trim().take(MAX_CHARS)
        if (text.isEmpty()) {
            if (announce) messenger.post("No text was found.")
            return false
        }
        repository.setOcrText(documentId, text)
        if (announce) messenger.post("Text indexed. You can now search for it in the library.")
        return true
    }

    private companion object {
        const val MAX_PAGES = 500
        const val MAX_CHARS = 400_000
    }
}
