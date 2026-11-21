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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
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
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val documents = container.documents
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of documents whose recognised text contains the query; null when not searching.
     *  A failed lookup (e.g. malformed stored OCR text) must not propagate: left unguarded, an
     *  exception here used to permanently kill the `state` StateFlow below, freezing the whole
     *  Library screen (not just search) until the screen was left and reopened. */
    private val textMatches = query
        .debounce(250)
        .mapLatest { text ->
            if (text.isBlank()) {
                null
            } else {
                try {
                    documents.idsMatchingText(text.trim()).toSet()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    emptySet()
                }
            }
        }

    private val source = combine(documents.summaries, documents.folders, container.settings.settings) { docs, folders, settings ->
        Triple(docs, folders, settings)
    }

    private val view = combine(query, filter, textMatches) { text, current, matches ->
        Triple(text, current, matches)
    }

    val state: StateFlow<LibraryUiState> = combine(source, view, selection) { (docs, folders, settings), (text, current, matches), selected ->
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
        val sorted = when (settings.sortOrder) {
            SortOrder.Newest -> visible
            SortOrder.Name -> visible.sortedBy { it.name.lowercase() }
            SortOrder.Size -> visible.sortedByDescending { it.sizeBytes }
        }.sortedBy { !it.pinned } // pinned documents stay on top; the sort above is kept within each group
        LibraryUiState(
            documents = sorted,
            folders = folders,
            settings = settings,
            query = text,
            filter = current,
            selection = selected.intersect(docs.map { it.id }.toSet()),
            totalCount = docs.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(text: String) {
        query.value = text
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
            ids.forEach { documents.setFavorite(it, favorite) }
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
            if (thenMove.isNotEmpty()) {
                documents.moveToFolder(thenMove.toList(), id)
                selection.value = emptySet()
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch { documents.renameFolder(id, name) }
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
