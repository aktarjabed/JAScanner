package aktarjabed.com.jaascanner.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.PageEntry
import aktarjabed.com.jaascanner.core.PageList
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.PagePlan
import aktarjabed.com.jaascanner.pdf.PdfPageSource
import aktarjabed.com.jaascanner.pdf.PdfTools
import aktarjabed.com.jaascanner.pdf.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PagesUiState(
    val document: DocumentEntity? = null,
    val source: PdfPageSource? = null,
    val loading: Boolean = true,
    val entries: List<PageEntry> = emptyList(),
    /** Ids of the selected entries (see [PageEntry.id]). */
    val selected: Set<Int> = emptySet(),
    val originalCount: Int = 0,
    val saving: Boolean = false,
) {
    val changed: Boolean get() = !PageList.isUnchanged(entries, originalCount)
}

/** The page organiser: works on a list in memory and writes a new copy only when the user saves. */
class PagesViewModel(
    private val container: AppContainer,
    private val documentId: Long,
) : ViewModel() {

    private val mutableState = MutableStateFlow(PagesUiState())
    val state: StateFlow<PagesUiState> = mutableState.asStateFlow()

    private var nextId = 0

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val document = container.documents.get(documentId)
        val source = document?.let { PdfPageSource.open(container.documents.fileOf(it)) }
        val entries = if (source != null) PageList.initial(source.pageCount) else emptyList()
        nextId = entries.size
        mutableState.update {
            it.copy(
                document = document,
                source = source,
                loading = false,
                entries = entries,
                originalCount = entries.size,
            )
        }
    }

    fun toggle(id: Int) = mutableState.update { current ->
        current.copy(selected = if (id in current.selected) current.selected - id else current.selected + id)
    }

    fun selectAll() = mutableState.update { current -> current.copy(selected = current.entries.map { it.id }.toSet()) }

    fun clearSelection() = mutableState.update { it.copy(selected = emptySet()) }

    fun indexOfId(id: Int): Int = PageList.indexOfId(mutableState.value.entries, id)

    /** Drag and drop: the page at [from] jumps to index [to]. */
    fun move(from: Int, to: Int) = mutableState.update { it.copy(entries = PageList.move(it.entries, from, to)) }

    fun shift(delta: Int) = mutableState.update { current ->
        current.copy(entries = PageList.shift(current.entries, current.selected, delta))
    }

    /** Puts the chosen pages as a block starting at [position] (1 = first page). */
    fun moveSelectedTo(position: Int) = mutableState.update { current ->
        current.copy(entries = PageList.moveTo(current.entries, current.selected, position))
    }

    fun rotateSelected(degrees: Int) = mutableState.update { current ->
        current.copy(entries = PageList.rotate(current.entries, current.selected, degrees))
    }

    fun deleteSelected() {
        val current = mutableState.value
        if (current.selected.isEmpty()) return
        val remaining = PageList.delete(current.entries, current.selected)
        if (remaining.size == current.entries.size) {
            container.messenger.post("A document needs at least one page.")
            return
        }
        mutableState.update { it.copy(entries = remaining, selected = emptySet()) }
    }

    fun duplicateSelected() {
        val current = mutableState.value
        if (current.selected.isEmpty()) return
        val firstNew = maxOf(nextId, PageList.nextId(current.entries))
        val copies = PageList.duplicate(current.entries, current.selected, firstNew)
        nextId = firstNew + (copies.size - current.entries.size)
        mutableState.update { it.copy(entries = copies, selected = emptySet()) }
    }

    /** Saves the new page order as a new document; the original is never touched. */
    fun save(onSaved: (Long) -> Unit) {
        val current = mutableState.value
        val document = current.document ?: return
        if (current.saving || !current.changed) return
        mutableState.update { it.copy(saving = true) }
        container.appScope.launch {
            val target = container.documents.newOutputFile()
            try {
                val plan = current.entries.map { PagePlan(it.source, it.rotation) }
                PdfTools.rearrange(container.documents.fileOf(document), plan, target)
                val saved = container.documents.registerOutput(
                    file = target,
                    name = document.name + " (pages changed)",
                    source = DocumentEntity.SOURCE_TOOL,
                    folderId = document.folderId,
                )
                if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                container.messenger.post("Saved as a new copy with ${saved.pageCount} pages. The original is unchanged.")
                withContext(Dispatchers.Main) { onSaved(saved.id) }
            } catch (error: CancellationException) {
                target.delete()
                throw error
            } catch (error: Throwable) {
                target.delete()
                container.messenger.post(error.toUserMessage())
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        val source = mutableState.value.source ?: return
        container.appScope.launch { source.release() }
    }
}
