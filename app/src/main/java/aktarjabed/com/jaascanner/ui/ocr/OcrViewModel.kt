package aktarjabed.com.jaascanner.ui.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.data.OcrLanguage
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.PdfTextExtractor
import aktarjabed.com.jaascanner.pdf.PdfTextLayer
import aktarjabed.com.jaascanner.pdf.RecognisedLine
import aktarjabed.com.jaascanner.pdf.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which operation produced the text currently on screen. */
enum class OcrSource { None, Document, Image }

data class OcrUiState(
    val document: DocumentEntity? = null,
    val resultSource: OcrSource = OcrSource.None,
    val language: OcrLanguage = OcrLanguage.Latin,
    /** Recognised text, one entry per page (a single entry for a picture). */
    val pages: List<String> = emptyList(),
    val running: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val error: String? = null,
    val hasRun: Boolean = false,
    /** Where each recognised line sits, by page index. Empty until the document has been read in this visit. */
    val lines: Map<Int, List<RecognisedLine>> = emptyMap(),
    /** True while a searchable copy is being written. */
    val embedding: Boolean = false,
) {
    /** The searchable copy needs the line positions, which only a fresh read produces. */
    val canEmbed: Boolean get() = lines.values.any { it.isNotEmpty() }

    /** Text as shown on screen; multi-page documents get a heading per page. */
    val displayText: String
        get() = if (pages.size <= 1) pages.firstOrNull().orEmpty()
        else pages.mapIndexed { index, text -> "--- Page ${index + 1} ---\n$text" }.joinToString("\n\n")

    val plainText: String get() = pages.joinToString("\n\n").trim()
}

class OcrViewModel(
    private val container: AppContainer,
    private val documentId: Long?,
) : ViewModel() {

    private val mutableState = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Incremented for every read; only the newest run may publish progress or final state. */
    private var generation = 0

    init {
        viewModelScope.launch {
            val language = container.settings.settings.first().ocrLanguage
            val document = documentId?.let { container.documents.get(it) }
            // Text found earlier (for example right after scanning) is shown straight away.
            val earlier = document?.ocrText.orEmpty()
            mutableState.update {
                it.copy(
                    document = document,
                    language = language,
                    pages = if (earlier.isNotBlank()) listOf(earlier) else emptyList(),
                    resultSource = if (earlier.isNotBlank()) OcrSource.Document else OcrSource.None,
                    hasRun = earlier.isNotBlank(),
                )
            }
            if (document != null && earlier.isBlank()) readDocument()
        }
    }

    fun setLanguage(language: OcrLanguage) {
        mutableState.update { it.copy(language = language) }
        viewModelScope.launch { container.settings.setOcrLanguage(language) }
    }

    fun readDocument() {
        val document = mutableState.value.document ?: return
        val language = mutableState.value.language
        start { token ->
            val pages = container.ocr.recognizePdfPages(
                file = container.documents.fileOf(document),
                language = language,
                maxPages = MAX_PAGES,
            ) { done, total ->
                if (token == generation) mutableState.update { it.copy(progress = done to total) }
            }
            if (token == generation) mutableState.update {
                it.copy(
                    resultSource = OcrSource.Document,
                    pages = pages.map { page -> page.text },
                    lines = pages.mapIndexed { index, page -> index to page.lines }.toMap(),
                )
            }
        }
    }

    fun readImage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        val language = mutableState.value.language
        start { token ->
            val text = container.ocr.recognizeImage(appContext, uri, language)
            if (token == generation) mutableState.update {
                it.copy(resultSource = OcrSource.Image, pages = listOf(text), lines = emptyMap())
            }
        }
    }

    private fun start(work: suspend (Int) -> Unit) {
        job?.cancel()
        val token = ++generation
        // Clear the old source as well: while a new read runs, document-only actions must not
        // act on text left over from the previous (possibly picture) read.
        mutableState.update {
            it.copy(running = true, error = null, progress = null, hasRun = false, resultSource = OcrSource.None)
        }
        job = viewModelScope.launch {
            try {
                work(token)
                if (token == generation) mutableState.update { it.copy(hasRun = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (token == generation) mutableState.update { it.copy(error = error.toUserMessage()) }
            } finally {
                if (token == generation) mutableState.update { it.copy(running = false, progress = null) }
            }
        }
    }

    /** Stores the text so the library search can find this document. */
    fun makeSearchable() {
        val current = mutableState.value
        val document = current.document ?: return
        if (current.resultSource != OcrSource.Document || current.running) {
            container.messenger.post("Read this document first; text from a picture is not indexed.")
            return
        }
        val text = current.plainText
        if (text.isBlank()) {
            container.messenger.post("There is no text to index.")
            return
        }
        viewModelScope.launch {
            container.documents.setOcrText(document.id, text.take(MAX_INDEX_CHARS))
            container.messenger.post("Done. You can now find this document by its text in the library.")
        }
    }

    /**
     * Writes a new copy of the document with the recognised text hidden under each scanned page, so
     * the PDF becomes searchable and selectable in any reader. Pages that already have real text are
     * left alone; the original document is not changed.
     */
    fun saveSearchablePdf() {
        val current = mutableState.value
        val document = current.document ?: return
        if (current.embedding || current.running) return
        if (current.resultSource != OcrSource.Document) {
            container.messenger.post("Read this document first; a picture has no page positions to embed.")
            return
        }
        if (!current.canEmbed) {
            container.messenger.post("Read the document first; the positions of the words are needed for the searchable copy.")
            return
        }
        mutableState.update { it.copy(embedding = true) }
        container.appScope.launch {
            val source = container.documents.fileOf(document)
            val target = container.documents.newOutputFile()
            try {
                // Do not stack a second text layer on pages that already carry text.
                val existing = PdfTextExtractor.pageTexts(source, maxPages = MAX_PAGES)
                val wanted = current.lines.filter { (page, lines) ->
                    lines.isNotEmpty() && existing.getOrNull(page).orEmpty().isBlank()
                }
                if (wanted.isEmpty()) {
                    container.messenger.post("Every page that was read already has text, so nothing was added.")
                    return@launch
                }
                val layer = PdfTextLayer.add(source, target, wanted)
                val saved = container.documents.registerOutput(
                    file = target,
                    name = document.name + " (searchable)",
                    source = DocumentEntity.SOURCE_TOOL,
                    folderId = document.folderId,
                )
                container.documents.setOcrText(saved.id, current.plainText.take(MAX_INDEX_CHARS))
                val dropped = if (layer.hasDroppedCharacters) {
                    " Some characters could not be drawn: ${layer.droppedCharacters.take(12)}."
                } else {
                    ""
                }
                container.messenger.post(
                    "Saved a searchable copy (${layer.pagesWritten} pages got a text layer). The original is unchanged.$dropped",
                )
            } catch (error: CancellationException) {
                target.delete()
                throw error
            } catch (error: Throwable) {
                target.delete()
                container.messenger.post(error.toUserMessage())
            } finally {
                mutableState.update { it.copy(embedding = false) }
            }
        }
    }

    companion object {
        const val MAX_PAGES = 500
        private const val MAX_INDEX_CHARS = 400_000
    }
}
