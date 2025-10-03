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
import com.jascanner.domain.model.*
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
    private val errorHandler: ErrorHandler,
    private val memoryManager: MemoryManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: String = savedStateHandle.get<String>("documentId") ?: ""

    // ✅ FIXED: Added simple uiState for EditorScreen compatibility
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Keep the detailed state for internal logic
    private val _detailedState = MutableStateFlow<DetailedEditorState>(DetailedEditorState.Loading)
    val detailedState: StateFlow<DetailedEditorState> = _detailedState.asStateFlow()

    private val _document = MutableStateFlow<EditableDocument?>(null)
    val document: StateFlow<EditableDocument?> = _document.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    init {
        if (documentId.isBlank()) {
            _detailedState.value = DetailedEditorState.Error(
                message = "Invalid document ID.",
                error = IllegalArgumentException("Document ID is missing or invalid."),
                recoverable = false
            )
            // ✅ Set simple error for UI
            _uiState.value = _uiState.value.copy(error = "Invalid document ID.")
        } else {
            loadDocument()
        }

        viewModelScope.launch {
            _currentPageIndex.collect {
                loadBitmapForCurrentPage()
            }
        }
    }

    // ✅ ADDED: clearError method for EditorScreen compatibility
    fun clearError() {
        _uiState.update { it.copy(error = null) }
        if (_detailedState.value is DetailedEditorState.Error) {
            val errorState = _detailedState.value as DetailedEditorState.Error
            if (errorState.recoverable) {
                _detailedState.value = DetailedEditorState.Ready
            }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _detailedState.value = DetailedEditorState.Loading
                _uiState.update { it.copy(isLoading = true, error = null) }

                val loadedDocument = documentRepository.loadEditableDocument(documentId)
                if (loadedDocument == null) {
                    val errorMsg = "Document not found"
                    _detailedState.value = DetailedEditorState.Error(
                        message = errorMsg,
                        error = IllegalStateException("Document with id $documentId does not exist"),
                        recoverable = false
                    )
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                    return@launch
                }

                _document.value = loadedDocument
                _detailedState.value = DetailedEditorState.Ready
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        document = loadedDocument
                    )
                }
                loadBitmapForCurrentPage()
            } catch (e: Exception) {
                errorHandler.recordException(e, "Failed to load document")
                val errorMsg = "Failed to load document: ${e.message}"
                _detailedState.value = DetailedEditorState.Error(
                    message = errorMsg,
                    error = e,
                    recoverable = true
                )
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }

    private fun loadBitmapForCurrentPage() {
        viewModelScope.launch {
            val doc = _document.value ?: return@launch
            val pageIndex = _currentPageIndex.value
            if (pageIndex < 0 || pageIndex >= doc.pages.size) return@launch

            val page = doc.pages[pageIndex]
            if (page.originalBitmap != null) return@launch

            withContext(Dispatchers.IO) {
                try {
                    // ✅ IMPROVED: Memory-safe bitmap loading
                    val uri = Uri.parse(page.originalImageUri)
                    val bitmap = memoryManager.loadBitmapSafely(context, uri)

                    val updatedPage = page.copy(originalBitmap = bitmap)
                    val updatedPages = doc.pages.toMutableList().apply { set(pageIndex, updatedPage) }
                    val updatedDoc = doc.copy(pages = updatedPages)

                    _document.value = updatedDoc
                    _uiState.update { it.copy(document = updatedDoc) }
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to load bitmap for page ${page.pageId}")
                    val errorMsg = "Failed to load page image."
                    _detailedState.value = DetailedEditorState.Error(
                        message = errorMsg,
                        error = e,
                        recoverable = true
                    )
                    _uiState.update { it.copy(error = errorMsg) }
                }
            }
        }
    }

    fun rotate(degrees: Float) {
        viewModelScope.launch {
            val doc = _document.value ?: return@launch
            val pageIndex = _currentPageIndex.value
            if (pageIndex < 0 || pageIndex >= doc.pages.size) return@launch

            val page = doc.pages[pageIndex]
            val bitmapToRotate = page.processedBitmap ?: page.originalBitmap ?: return@launch

            withContext(Dispatchers.Default) {
                try {
                    // ✅ IMPROVED: Safe bitmap rotation with memory management
                    val matrix = Matrix().apply { postRotate(degrees) }
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmapToRotate, 0, 0, bitmapToRotate.width, bitmapToRotate.height, matrix, true
                    )

                    // Clean up old processed bitmap if it exists
                    page.processedBitmap?.let { oldBitmap ->
                        if (oldBitmap != bitmapToRotate && !oldBitmap.isRecycled) {
                            BitmapUtils.safeRecycle(oldBitmap)
                        }
                    }

                    val updatedPage = page.copy(processedBitmap = rotatedBitmap)
                    val updatedPages = doc.pages.toMutableList().apply { set(pageIndex, updatedPage) }
                    val updatedDoc = doc.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())

                    _document.value = updatedDoc
                    _uiState.update { it.copy(document = updatedDoc) }
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to rotate image")
                    val errorMsg = "Failed to rotate image."
                    _uiState.update { it.copy(error = errorMsg) }
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            _document.value?.let { doc ->
                try {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                    documentRepository.saveEditableDocument(doc)
                    _uiState.update { it.copy(isLoading = false) }
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to save document")
                    val errorMsg = "Failed to save changes."
                    _detailedState.value = DetailedEditorState.Error(
                        message = errorMsg,
                        error = e,
                        recoverable = true
                    )
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
            }
        }
    }

    fun setCurrentPageIndex(index: Int) {
        if (index != _currentPageIndex.value) {
            _currentPageIndex.value = index
        }
    }

    fun retryLoad() {
        if (documentId.isNotBlank()) {
            loadDocument()
        }
    }

    // ✅ IMPROVED: Enhanced cleanup with better memory management
    override fun onCleared() {
        super.onCleared()
        try {
            _document.value?.pages?.forEach { page ->
                BitmapUtils.safeRecycle(page.originalBitmap)
                BitmapUtils.safeRecycle(page.processedBitmap)
                BitmapUtils.safeRecycle(page.thumbnail)
                memoryManager.removeBitmapFromCache(page.pageId)
            }
            Timber.d("EditorViewModel cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error during EditorViewModel cleanup")
        }
    }
}

// ✅ ADDED: Simple UI state for EditorScreen compatibility
data class EditorUiState(
    val isLoading: Boolean = false,
    val error: String? = null,  // Nullable for safe handling
    val document: EditableDocument? = null
)

// Keep detailed state for internal logic
sealed class DetailedEditorState {
    object Loading : DetailedEditorState()
    object Ready : DetailedEditorState()
    data class Error(
        val message: String,
        val error: Throwable,
        val recoverable: Boolean
    ) : DetailedEditorState()
}