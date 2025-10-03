package com.jascanner.presentation.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.core.ErrorHandler
import com.jascanner.core.MemoryManager
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.data.repository.EditorRepository
import com.jascanner.domain.model.*
import com.jascanner.utils.ValidationUtils
import com.jascanner.utils.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val editorRepository: EditorRepository,
    private val errorHandler: ErrorHandler,
    private val memoryManager: MemoryManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // single, well-typed documentId (long)
    private val documentId: Long = savedStateHandle.get<String>("documentId")?.toLongOrNull() ?: -1L

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // ensure current page index exists
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    init {
        if (documentId == -1L) {
            _uiState.value = EditorUiState.Error(
                message = "Invalid document ID.",
                error = IllegalArgumentException("Document ID is missing or invalid."),
                recoverable = false
            )
        } else {
            loadDocument()
            // observe page index changes
            viewModelScope.launch {
                _currentPageIndex.collect {
                    loadBitmapForCurrentPage()
                }
            }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = EditorUiState.Loading
                val loadedDocument = documentRepository.loadEditableDocument(documentId)

                if (loadedDocument == null) {
                    _uiState.value = EditorUiState.Error(
                        message = "Document not found",
                        error = IllegalStateException("No document for id: $documentId"),
                        recoverable = false
                    )
                    return@launch
                }

                // convert to UI state or whatever existing mapping is intended
                _uiState.value = EditorUiState.Ready(loadedDocument)
            } catch (t: Throwable) {
                Timber.e(t)
                val handled = errorHandler.handle(t)
                _uiState.value = EditorUiState.Error(
                    message = handled.message ?: "Unknown error while loading document",
                    error = t,
                    recoverable = handled.recoverable
                )
            }
        }
    }

    private suspend fun loadBitmapForCurrentPage() {
        // minimal placeholder - replace with the real implementation if more logic exists
        withContext(Dispatchers.IO) {
            try {
                val idx = _currentPageIndex.value
                val doc = (uiState.value as? EditorUiState.Ready)?.document ?: return@withContext
                val pageUri: Uri = doc.getPageUri(idx) // adjust to your model
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, pageUri)
                // cache bitmap using memoryManager
                memoryManager.cacheBitmapForPage(documentId, idx, bitmap)
                // you might emit ui updates here...
            } catch (t: Throwable) {
                Timber.w(t, "Failed to load bitmap for page")
            }
        }
    }

    // Additional editor functionality omitted for brevity — keep the rest of the PR changes as intended.
}