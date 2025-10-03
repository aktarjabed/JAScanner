package com.jascanner.presentation.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.core.ErrorHandler
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.export.ExportManager
import com.jascanner.utils.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val exportManager: ExportManager,
    private val fileManager: FileManager,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _ui = MutableStateFlow(DocumentListUiState())
    val ui: StateFlow<DocumentListUiState> = _ui.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            documentRepository.getAllDocuments()
                .catch { e ->
                    errorHandler.recordException(e, "Failed to load documents")
                    _ui.value = _ui.value.copy(isLoading = false, error = e.message)
                }
                .collect { docs ->
                    _ui.value = _ui.value.copy(isLoading = false, documents = docs)
                }
        }
    }

    fun createNewDocument(title: String) {
        viewModelScope.launch {
            try {
                documentRepository.createNewDocument(title, null)
            } catch (e: Exception) {
                errorHandler.recordException(e, "Failed to create document")
                _ui.value = _ui.value.copy(error = "Failed to create document")
            }
        }
    }

    fun share(doc: DocumentEntity) = viewModelScope.launch {
        try {
            val document = documentRepository.loadEditableDocument(doc.id)
            if (document == null) {
                _ui.value = _ui.value.copy(error = "Document not found")
                return@launch
            }

            val out: File = fileManager.createTempFile("share_${doc.id}", ".pdf")
            val imageUris = document.pages.map { it.originalImageUri }
            val ok = exportManager.exportToPdf(imageUris, out)

            if (ok) {
                _ui.value = _ui.value.copy(message = "Ready to share", shareFile = out)
            } else {
                _ui.value = _ui.value.copy(error = "Share export failed")
            }
        } catch (e: Exception) {
            errorHandler.recordException(e, "Failed to share document")
            _ui.value = _ui.value.copy(error = e.message)
        }
    }

    fun delete(doc: DocumentEntity) = viewModelScope.launch {
        try {
            documentRepository.deleteDocument(doc)
            _ui.value = _ui.value.copy(message = "Deleted")
        } catch (e: Exception) {
            errorHandler.recordException(e, "Failed to delete document")
            _ui.value = _ui.value.copy(error = e.message)
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, shareFile = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}

data class DocumentListUiState(
    val isLoading: Boolean = false,
    val documents: List<DocumentEntity> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val shareFile: File? = null
)