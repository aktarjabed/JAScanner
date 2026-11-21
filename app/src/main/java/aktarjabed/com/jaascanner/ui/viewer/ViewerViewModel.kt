package aktarjabed.com.jaascanner.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.Bookmark
import aktarjabed.com.jaascanner.pdf.NoteInfo
import aktarjabed.com.jaascanner.pdf.PdfBookmarks
import aktarjabed.com.jaascanner.pdf.PdfNotes
import aktarjabed.com.jaascanner.pdf.PdfPageSource
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.pdf.PdfTextExtractor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ViewerUiState(
    val document: DocumentEntity? = null,
    val source: PdfPageSource? = null,
    val loading: Boolean = true,
    val unreadable: Boolean = false,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchMatches: List<Int> = emptyList(),
    val searchIndex: Int = 0,
    /** Null until a search has run; false when the PDF has no embedded text (a plain scan). */
    val hasTextLayer: Boolean? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val notes: List<NoteInfo> = emptyList(),
    val panelLoading: Boolean = false,
    val panelBusy: Boolean = false,
)

class ViewerViewModel(
    private val container: AppContainer,
    private val documentId: Long,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ViewerUiState())
    private val changeMutex = Mutex()
    val state: StateFlow<ViewerUiState> = mutableState.asStateFlow()

    private var pageTexts: List<String>? = null

    /** Text extraction runs once for the whole document and is shared by every search keystroke. */
    private var extraction: Deferred<List<String>>? = null
    private var searchJob: Job? = null
    private var panelJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        // Start from a clean slate: a failed re-open must not leave marks or search hits from a
        // document that is no longer loaded.
        mutableState.update {
            it.copy(
                loading = true,
                unreadable = false,
                searchMatches = emptyList(),
                searchIndex = 0,
                searching = false,
                hasTextLayer = null,
                bookmarks = emptyList(),
                notes = emptyList(),
                panelLoading = false,
                panelBusy = false,
            )
        }
        val document = container.documents.get(documentId)
        if (document == null) {
            mutableState.update { it.copy(loading = false, unreadable = true) }
            return
        }
        val source = PdfPageSource.open(container.documents.fileOf(document))
        mutableState.update {
            it.copy(document = document, source = source, loading = false, unreadable = source == null)
        }
    }

    fun file(): File? = mutableState.value.document?.let { container.documents.fileOf(it) }

    private suspend fun reloadDocument() {
        val document = container.documents.get(documentId) ?: return
        mutableState.update { it.copy(document = document) }
    }

    fun toggleFavorite() {
        val document = mutableState.value.document ?: return
        viewModelScope.launch {
            container.documents.setFavorite(document.id, !document.favorite)
            reloadDocument()
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            container.documents.rename(documentId, name)
            reloadDocument()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            container.documents.delete(listOf(documentId))
            container.messenger.post("Document deleted.")
            onDone()
        }
    }

    suspend fun saveCopyTo(context: Context, uri: Uri): Boolean {
        val source = file() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                output.use { out -> source.inputStream().use { it.copyTo(out) } }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                false
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        // Clear immediately: during the debounce old matches must not look current or let
        // Next/Prev jump to pages from a previous query.
        mutableState.update {
            it.copy(
                searchQuery = query,
                searchMatches = emptyList(),
                searchIndex = 0,
                searching = query.isNotBlank(),
                hasTextLayer = null,
            )
        }
        if (query.isBlank()) return
        val target = file() ?: run {
            mutableState.update { it.copy(searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val texts = pageTexts ?: try {
                val running = extraction ?: viewModelScope.async { PdfTextExtractor.pageTexts(target) }.also { extraction = it }
                running.await().also { pageTexts = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                extraction = null
                emptyList()
            }
            val matches = PdfTextExtractor.pagesContaining(texts, query)
            // Cancellation can arrive while the non-suspending match search above is running; do
            // not publish those results over the next query's freshly cleared state.
            ensureActive()
            mutableState.update {
                it.copy(
                    searching = false,
                    searchMatches = matches,
                    searchIndex = 0,
                    hasTextLayer = texts.any { page -> page.isNotBlank() },
                )
            }
        }
    }

    /** Reads the bookmarks and comments stored in the PDF, for the Marks panel. */
    fun loadPanel() {
        val target = file() ?: return
        panelJob?.cancel()
        panelJob = viewModelScope.launch {
            mutableState.update {
                it.copy(panelLoading = true, bookmarks = emptyList(), notes = emptyList())
            }
            val bookmarks = try {
                PdfBookmarks.read(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            val notes = try {
                PdfNotes.read(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            ensureActive()
            mutableState.update { it.copy(bookmarks = bookmarks, notes = notes, panelLoading = false) }
        }
    }

    fun addBookmark(title: String, page: Int) =
        change("Bookmark added.") { source, target -> PdfBookmarks.add(source, target, title, page) }

    fun removeBookmark(bookmark: Bookmark) =
        change("Bookmark removed.") { source, target -> PdfBookmarks.remove(source, target, bookmark) }

    fun removeNote(note: NoteInfo) =
        change("Comment removed.") { source, target -> PdfNotes.remove(source, target, note) }

    /**
     * Small changes that live inside the PDF (bookmarks, comments) are written into the same
     * document. The new file is checked first; if anything fails the document stays as it was.
     */
    private fun change(done: String, work: suspend (source: File, target: File) -> Unit) {
        container.appScope.launch {
            // Bookmarks and comments rewrite the same PDF. Serialise them so two quick taps
            // cannot both read the old file and overwrite each other, and read the latest
            // document only once it is this operation's turn.
            changeMutex.withLock {
                val document = mutableState.value.document ?: return@withLock
                mutableState.update { it.copy(panelBusy = true) }
                val target = container.documents.newOutputFile()
                try {
                    work(container.documents.fileOf(document), target)
                    val updated = container.documents.replaceContent(document, target)
                    if (updated == null) {
                        container.messenger.post("That change could not be saved. The document is unchanged.")
                        return@withLock
                    }
                    val fresh = PdfPageSource.open(container.documents.fileOf(updated))
                    val old = mutableState.value.source
                    pageTexts = null
                    extraction = null
                    searchJob?.cancel()
                    panelJob?.cancel()
                    mutableState.update {
                        it.copy(
                            document = updated,
                            source = fresh,
                            unreadable = fresh == null,
                            searchMatches = emptyList(),
                            searchIndex = 0,
                            searching = false,
                            hasTextLayer = null,
                            bookmarks = emptyList(),
                            notes = emptyList(),
                        )
                    }
                    old?.release()
                    if (fresh != null) loadPanel()
                    container.messenger.post(done)
                } catch (error: CancellationException) {
                    target.delete()
                    throw error
                } catch (error: Throwable) {
                    target.delete()
                    container.messenger.post(error.toUserMessage())
                } finally {
                    mutableState.update { it.copy(panelBusy = false) }
                }
            }
        }
    }

    fun stepMatch(delta: Int) {
        mutableState.update { current ->
            if (current.searchMatches.isEmpty()) {
                current
            } else {
                current.copy(searchIndex = (current.searchIndex + delta).mod(current.searchMatches.size))
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        mutableState.update { it.copy(searchQuery = "", searchMatches = emptyList(), searchIndex = 0, searching = false) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }

    override fun onCleared() {
        val source = mutableState.value.source ?: return
        container.appScope.launch { source.release() }
    }
}
