package com.jascanner.presentation.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.export.ExportManager
import com.jascanner.utils.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val exportManager: ExportManager,
    private val fileManager: FileManager
) : ViewModel() {

    private val _ui = MutableStateFlow(DocumentDetailUiState())
    val ui: StateFlow<DocumentDetailUiState> = _ui.asStateFlow()

    fun load(docId: Long) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true)
            try {
                documentRepository.getDocumentById(docId)
                    .filterNotNull()
                    .collect { doc ->
                        _ui.value = _ui.value.copy(
                            document = doc,
                            isLoading = false,
                            error = null
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load document details")
                _ui.value = _ui.value.copy(error = "Failed to load document details", isLoading = false)
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            val doc = _ui.value.document ?: return@launch
            try {
                val document = documentRepository.loadEditableDocument(doc.id)
                if (document == null) {
                    _ui.value = _ui.value.copy(error = "Document not found")
                    return@launch
                }

                val out: File = fileManager.createTempFile("export_${doc.id}", ".pdf")
                val imageUris = document.pages.map { it.originalImageUri }
                val ok = exportManager.exportToPdf(imageUris, out)

                if (ok) {
                    _ui.value = _ui.value.copy(message = "Exported to ${out.name}", exportedFile = out)
                } else {
                    _ui.value = _ui.value.copy(error = "PDF export failed")
                }
            } catch (e: Exception) {
                Timber.e(e)
                _ui.value = _ui.value.copy(error = "An unexpected error occurred during export.")
            }
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, exportedFile = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}

data class DocumentDetailUiState(
    val isLoading: Boolean = true,
    val document: DocumentEntity? = null,
    val error: String? = null,
    val message: String? = null,
    val exportedFile: File? = null
)