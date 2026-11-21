package aktarjabed.com.jaascanner.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.TextReflow
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.PdfTextExtractor
import aktarjabed.com.jaascanner.pdf.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A line of the reading view: a page marker, or a paragraph (numbered among paragraphs only). */
data class ReaderRow(val page: Int, val paragraph: Int?, val text: String)

data class ReaderUiState(
    val document: DocumentEntity? = null,
    val loading: Boolean = true,
    val rows: List<ReaderRow> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasText: Boolean get() = paragraphs.isNotEmpty()
}

/** Pulls the real text out of the PDF and reflows it into paragraphs. Scans without a text layer give none. */
class ReaderViewModel(
    private val container: AppContainer,
    private val documentId: Long,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val document = container.documents.get(documentId)
            if (document == null) {
                mutableState.update { it.copy(loading = false, error = "This document no longer exists.") }
                return@launch
            }
            try {
                val pageTexts = PdfTextExtractor.pageTexts(container.documents.fileOf(document))
                val rows = ArrayList<ReaderRow>()
                val paragraphs = ArrayList<String>()
                for ((page, text) in pageTexts.withIndex()) {
                    val found = TextReflow.paragraphs(text)
                    if (found.isEmpty()) continue
                    rows.add(ReaderRow(page, null, "Page ${page + 1}"))
                    for (paragraph in found) {
                        rows.add(ReaderRow(page, paragraphs.size, paragraph))
                        paragraphs.add(paragraph)
                    }
                }
                mutableState.update { it.copy(document = document, loading = false, rows = rows, paragraphs = paragraphs) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update { it.copy(document = document, loading = false, error = error.toUserMessage()) }
            }
        }
    }
}
