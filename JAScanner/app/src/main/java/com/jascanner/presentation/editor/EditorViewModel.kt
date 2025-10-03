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

    private val documentId: Long = savedStateHandle.get<String>("documentId")?.toLongOrNull() ?: -1

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _document = MutableStateFlow<EditableDocument?>(null)
    val document: StateFlow<EditableDocument?> = _document.asStateFlow()

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
        }

        viewModelScope.launch {
            _currentPageIndex.collect {
                loadBitmapForCurrentPage()
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
                        error = IllegalStateException("Document with id $documentId does not exist"),
                        recoverable = false
                    )
                    return@launch
                }
                _document.value = loadedDocument
                _uiState.value = EditorUiState.Ready
                loadBitmapForCurrentPage()
            } catch (e: Exception) {
                errorHandler.recordException(e, "Failed to load document")
                _uiState.value = EditorUiState.Error(
                    message = "Failed to load document: ${e.message}",
                    error = e,
                    recoverable = true
                )
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
                    val uri = Uri.parse(page.originalImageUri)
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    val updatedPage = page.copy(originalBitmap = bitmap)
                    val updatedPages = doc.pages.toMutableList().apply { set(pageIndex, updatedPage) }
                    _document.value = doc.copy(pages = updatedPages)
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to load bitmap for page ${page.pageId}")
                    _uiState.value = EditorUiState.Error(
                        message = "Failed to load page image.",
                        error = e,
                        recoverable = true
                    )
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
                    val matrix = Matrix().apply { postRotate(degrees) }
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmapToRotate, 0, 0, bitmapToRotate.width, bitmapToRotate.height, matrix, true
                    )
                    val updatedPage = page.copy(processedBitmap = rotatedBitmap)
                    val updatedPages = doc.pages.toMutableList().apply { set(pageIndex, updatedPage) }
                    _document.value = doc.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to rotate image")
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            _document.value?.let { doc ->
                try {
                    documentRepository.saveEditableDocument(doc)
                } catch (e: Exception) {
                    errorHandler.recordException(e, "Failed to save document")
                    _uiState.value = EditorUiState.Error(
                        message = "Failed to save changes.",
                        error = e,
                        recoverable = true
                    )
                }
            }
        }
    }

    fun setCurrentPageIndex(index: Int) {
        _currentPageIndex.value = index
    }

    fun retryLoad() {
        if (documentId != -1L) {
            loadDocument()
        }
    }

    override fun onCleared() {
        super.onCleared()
        _document.value?.pages?.forEach { page ->
            BitmapUtils.safeRecycle(page.originalBitmap)
            BitmapUtils.safeRecycle(page.processedBitmap)
            BitmapUtils.safeRecycle(page.thumbnail)
            memoryManager.removeBitmapFromCache(page.pageId)
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