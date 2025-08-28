package com.jascanner.presentation.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.export.ExportManager
import com.jascanner.repository.DocumentRepository
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
    private val fileManager: FileManager
) : ViewModel() {

    private val _ui = MutableStateFlow(DocumentListUiState())
    val ui: StateFlow<DocumentListUiState> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            documentRepository.getAllDocuments()
                .catch { e -> _ui.value = _ui.value.copy(isLoading = false, error = e.message) }
                .collect { docs -> _ui.value = _ui.value.copy(isLoading = false, documents = docs) }
        }
    }

    fun share(doc: DocumentEntity) = viewModelScope.launch {
        try {
            val out: File = fileManager.createTempFile("share_${doc.id}", ".pdf")
            val ok = exportManager.exportToPdf(doc.textContent, out)
            if (ok) _ui.value = _ui.value.copy(message = "Ready to share", shareFile = out)
            else _ui.value = _ui.value.copy(error = "Share export failed")
        } catch (e: Exception) { Timber.e(e); _ui.value = _ui.value.copy(error = e.message) }
    }

    fun delete(doc: DocumentEntity) = viewModelScope.launch {
        try { documentRepository.deleteDocument(doc); _ui.value = _ui.value.copy(message = "Deleted"); load() }
        catch (e: Exception) { _ui.value = _ui.value.copy(error = e.message) }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null, shareFile = null) }
    fun clearError() { _ui.value = _ui.value.copy(error = null) }
}

data class DocumentListUiState(
    val isLoading: Boolean = false,
    val documents: List<DocumentEntity> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val shareFile: java.io.File? = null
)

