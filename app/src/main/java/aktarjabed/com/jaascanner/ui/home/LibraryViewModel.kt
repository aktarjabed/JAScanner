package aktarjabed.com.jaascanner.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.FolderPath
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.data.AppSettings
import aktarjabed.com.jaascanner.data.SortOrder
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.data.db.DocumentSummary
import aktarjabed.com.jaascanner.data.db.FolderEntity
import aktarjabed.com.jaascanner.scan.ScanResult
import aktarjabed.com.jaascanner.pdf.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ids of a folder and everything below it, so a folder also shows its subfolders' documents. */
private fun inFolder(folders: List<FolderEntity>, id: Long): Set<Long> = FolderPath.descendants(folders, id)

sealed interface LibraryFilter {
    data object All : LibraryFilter

    data object Favorites : LibraryFilter

    data class Folder(val id: Long) : LibraryFilter
}

data class LibraryUiState(
    val documents: List<DocumentSummary> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val selection: Set<Long> = emptySet(),
    val totalCount: Int = 0,
    val loaded: Boolean = false,
    val searching: Boolean = false,
    val searchFailed: String? = null,
    val loadFailed: String? = null,
)

private sealed interface LibrarySource {
    data class Ready(
        val documents: List<DocumentSummary>,
        val folders: List<FolderEntity>,
        val settings: AppSettings,
    ) : LibrarySource

    data class Failed(val message: String) : LibrarySource
}

private sealed interface TextMatches {
    data object Idle : TextMatches
    data object Loading : TextMatches
    data class Found(val ids: Set<Long>) : TextMatches
    data class Failed(val message: String) : TextMatches
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val documents = container.documents
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val searchRetry = MutableStateFlow(0)
    private val sourceRetry = MutableStateFlow(0)

    /**
     * Ids of documents whose recognised text contains the query. A failed lookup is exposed as
     * [TextMatches.Failed] instead of an empty result: pretending "nothing matches" hid real
     * database errors, and letting the exception escape killed the whole Library StateFlow.
     */
    private val textMatches = combine(query, searchRetry) { text, _ -> text }
        .flatMapLatest { text ->
            if (text.isBlank()) {
                flowOf(TextMatches.Idle)
            } else {
                flow {
                    emit(TextMatches.Loading)
                    delay(250)
                    emit(TextMatches.Found(documents.idsMatchingText(text.trim()).toSet()))
                }.catch { error ->
                    if (error is CancellationException) throw error
                    emit(TextMatches.Failed(error.toUserMessage()))
                }
            }
        }

    /** Re-subscribing on retry lets a failed database flow recover without leaving the screen. */
    private val source = sourceRetry.flatMapLatest {
        combine(documents.summaries, documents.folders, container.settings.settings) { docs, folders, settings ->
            LibrarySource.Ready(docs, folders, settings) as LibrarySource
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(LibrarySource.Failed(error.toUserMessage()))
        }
    }

    private val view = combine(query, filter, textMatches) { text, current, matches ->
        Triple(text, current, matches)
    }

    val state: StateFlow<LibraryUiState> = combine(source, view, selection) { source, (text, current, matchState), selected ->
        if (source is LibrarySource.Failed) {
            LibraryUiState(
                query = text,
                filter = current,
                loaded = true,
                loadFailed = source.message,
                searchFailed = (matchState as? TextMatches.Failed)?.message,
                searching = matchState is TextMatches.Loading,
            )
        } else {
            val ready = source as LibrarySource.Ready
            val docs = ready.documents
            val folders = ready.folders
            val matches = (matchState as? TextMatches.Found)?.ids
            val needle = text.trim()
            val visible = docs
                .filter { doc ->
                    when (current) {
                        LibraryFilter.All -> true
                        LibraryFilter.Favorites -> doc.favorite
                        is LibraryFilter.Folder -> doc.folderId != null && doc.folderId in inFolder(folders, current.id)
                    }
                }
                .filter { doc ->
                    needle.isEmpty() || doc.name.contains(needle, ignoreCase = true) || matches?.contains(doc.id) == true
                }
            val sorted = when (ready.settings.sortOrder) {
                SortOrder.Newest -> visible
                SortOrder.Name -> visible.sortedBy { it.name.lowercase() }
                SortOrder.Size -> visible.sortedByDescending { it.sizeBytes }
            }.sortedBy { !it.pinned } // pinned documents stay on top; the sort above is kept within each group
            LibraryUiState(
                documents = sorted,
                folders = folders,
                settings = ready.settings,
                query = text,
                filter = current,
                selection = selected.intersect(docs.map { it.id }.toSet()),
                totalCount = docs.size,
                loaded = true,
                searching = matchState is TextMatches.Loading,
                searchFailed = (matchState as? TextMatches.Failed)?.message,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun retrySearch() {
        searchRetry.update { it + 1 }
    }

    fun retryLoad() {
        sourceRetry.update { it + 1 }
    }

    fun setQuery(text: String) {
        query.value = text
        // A query change rebuilds the visible list, so any old selection would be invisible and
        // Back would clear a selection the user could no longer see.
        selection.value = emptySet()
    }

    fun setFilter(value: LibraryFilter) {
        filter.value = value
        selection.value = emptySet()
    }

    fun toggleSelect(id: Long) {
        selection.update { if (id in it) it - id else it + id }
    }

    /** Selects every document currently shown (after search and folder filtering). */
    fun selectAll() {
        selection.value = state.value.documents.map { it.id }.toSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { documents.setFavorite(id, !current) }
    }

    fun setPinnedMany(ids: Collection<Long>, pinned: Boolean) {
        viewModelScope.launch {
            documents.setPinned(ids.toList(), pinned)
            selection.value = emptySet()
        }
    }

    fun setFavoriteMany(ids: Collection<Long>, favorite: Boolean) {
        viewModelScope.launch {
            documents.setFavorite(ids.toList(), favorite)
            selection.value = emptySet()
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { documents.rename(id, name) }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch {
            documents.delete(ids.toList())
            selection.value = emptySet()
            container.messenger.post(if (ids.size == 1) "Document deleted." else "${ids.size} documents deleted.")
        }
    }

    fun moveTo(ids: Collection<Long>, folderId: Long?) {
        viewModelScope.launch {
            documents.moveToFolder(ids.toList(), folderId)
            selection.value = emptySet()
        }
    }

    fun createFolder(name: String, thenMove: Collection<Long> = emptyList(), parentId: Long? = null) {
        viewModelScope.launch {
            val id = documents.createFolder(name, parentId)
            if (id == null) {
                container.messenger.post("Enter a folder name.")
                return@launch
            }
            if (thenMove.isNotEmpty()) {
                documents.moveToFolder(thenMove.toList(), id)
                selection.value = emptySet()
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            if (!documents.renameFolder(id, name)) {
                container.messenger.post("Enter a folder name.")
            }
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            documents.deleteFolder(id)
            if ((filter.value as? LibraryFilter.Folder)?.id == id) filter.value = LibraryFilter.All
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { container.settings.setSortOrder(order) }
    }

    fun setGridView(grid: Boolean) {
        viewModelScope.launch { container.settings.setGridView(grid) }
    }

    fun importPdf(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val document = documents.importFromUri(uri, null, DocumentEntity.SOURCE_IMPORT)
            if (document == null) {
                container.messenger.post("That file is not a readable PDF.")
            } else {
                if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(document.id)
                onDone(document.id)
            }
        }
    }

    fun saveScan(result: ScanResult, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val name = Formatters.defaultScanName(System.currentTimeMillis())
            // New scans go into the default folder from Settings, if that folder still exists.
            val folderId = container.settings.settings.first().defaultFolderId?.takeIf { documents.folderExists(it) }
            val document = documents.importFromUri(result.pdfUri, name, DocumentEntity.SOURCE_SCAN, folderId)
            if (document == null) {
                container.messenger.post("The scan could not be saved.")
                return@launch
            }
            if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(document.id)
            onSaved(document.id)
        }
    }
}
