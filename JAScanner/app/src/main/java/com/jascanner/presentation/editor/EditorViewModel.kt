package com.jascanner.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.data.repository.EditorRepository
import com.jascanner.domain.model.*
import com.jascanner.utils.ValidationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val editorRepository: EditorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"]) {
        "documentId is required"
    }

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _document = MutableStateFlow<EditableDocument?>(null)
    val document: StateFlow<EditableDocument?> = _document.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = EditorUiState.Loading

                // Add null safety check
                val loadedDocument = documentRepository.loadEditableDocument(documentId)

                if (loadedDocument == null) {
                    _uiState.value = EditorUiState.Error(
                        message = "Document not found",
                        error = IllegalStateException("Document with id $documentId does not exist"),
                        recoverable = false
                    )
                    return@launch
                }

                // Validate document
                val validation = ValidationUtils.validateDocument(loadedDocument)
                if (!validation.isValid) {
                    _uiState.value = EditorUiState.Error(
                        message = "Invalid document: ${validation.errors.firstOrNull()}",
                        error = IllegalStateException("Document validation failed"),
                        recoverable = true
                    )
                    return@launch
                }

                _document.value = loadedDocument
                _uiState.value = EditorUiState.Ready

                Timber.d("Document loaded successfully: ${loadedDocument.name}")

            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid document ID")
                _uiState.value = EditorUiState.Error(
                    message = "Invalid document: ${e.message}",
                    error = e,
                    recoverable = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load document")
                _uiState.value = EditorUiState.Error(
                    message = "Failed to load document: ${e.message}",
                    error = e,
                    recoverable = true
                )
            }
        }
    }

    fun retryLoad() {
        loadDocument()
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup resources
        _document.value?.pages?.forEach { page ->
            page.originalBitmap?.recycle()
            page.processedBitmap?.recycle()
            page.thumbnail?.recycle()
        }
        Timber.d("EditorViewModel cleared")
    }
}

sealed class EditorUiState {
    object Loading : EditorUiState()
    object Ready : EditorUiState()
    data class Error(
        val message: String,
        val error: Throwable,
        val recoverable: Boolean
    ) : EditorUiState()
}