package com.jascanner.edit.presentation

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.edit.data.repository.EditRepository
import com.jascanner.edit.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class EditUiState {
    object Idle : EditUiState()
    object Loading : EditUiState()
    data class Success(val document: EditableDocument) : EditUiState()
    data class Error(val message: String, val throwable: Throwable? = null) : EditUiState()
}

class EditViewModel(
    private val repository: EditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditUiState>(EditUiState.Idle)
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _editHistory = MutableStateFlow<List<EditAction>>(emptyList())
    val editHistory: StateFlow<List<EditAction>> = _editHistory.asStateFlow()

    fun loadDocument(document: EditableDocument) {
        _uiState.value = EditUiState.Success(document)
    }

    fun performOCR(pageIndex: Int, bitmap: Bitmap) {
        val currentDoc = (_uiState.value as? EditUiState.Success)?.document ?: return

        viewModelScope.launch {
            _uiState.value = EditUiState.Loading
            runCatching {
                repository.performOCR(bitmap)
            }.onSuccess { textBlocks ->
                val updatedDoc = currentDoc.let { doc ->
                    val updatedPages = doc.pages.toMutableList()
                    updatedPages[pageIndex] = updatedPages[pageIndex].copy(ocrTextLayer = textBlocks)
                    doc.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
                _uiState.value = EditUiState.Success(updatedDoc)
            }.onFailure { throwable ->
                _uiState.value = EditUiState.Error("Failed to perform OCR.", throwable)
            }
        }
    }

    fun cropPage(pageIndex: Int, bitmap: Bitmap, cropRect: RectF) {
        val currentDoc = (_uiState.value as? EditUiState.Success)?.document ?: return

        viewModelScope.launch {
            _uiState.value = EditUiState.Loading
            runCatching {
                val croppedBitmap = repository.cropBitmap(bitmap, cropRect)
                repository.saveBitmap(
                    croppedBitmap,
                    "cropped_${System.currentTimeMillis()}.png"
                )
            }.onSuccess { savedUri ->
                val updatedDoc = currentDoc.let { doc ->
                    val updatedPages = doc.pages.toMutableList()
                    updatedPages[pageIndex] = updatedPages[pageIndex].copy(
                        processedImageUri = savedUri,
                        cropRect = cropRect
                    )
                    doc.copy(
                        pages = updatedPages,
                        modifiedAt = System.currentTimeMillis(),
                        signatureInvalidated = doc.hasSignature
                    )
                }
                _uiState.value = EditUiState.Success(updatedDoc)
            }.onFailure { throwable ->
                _uiState.value = EditUiState.Error("Failed to crop page.", throwable)
            }
        }
    }

    fun addAnnotations(pageIndex: Int, annotations: List<Annotation>) {
        val currentDoc = (_uiState.value as? EditUiState.Success)?.document ?: return

        val updatedDoc = currentDoc.let { doc ->
            val updatedPages = doc.pages.toMutableList()
            updatedPages[pageIndex] = updatedPages[pageIndex].copy(
                annotations = updatedPages[pageIndex].annotations + annotations
            )
            doc.copy(
                pages = updatedPages,
                modifiedAt = System.currentTimeMillis(),
                signatureInvalidated = doc.hasSignature
            )
        }
        _uiState.value = EditUiState.Success(updatedDoc)
    }

    fun exportPDF(outputFile: File, onComplete: (Result<File>) -> Unit) {
        val currentDoc = (_uiState.value as? EditUiState.Success)?.document ?: run {
            onComplete(Result.failure(IllegalStateException("No document loaded to export.")))
            return
        }

        viewModelScope.launch {
            _uiState.value = EditUiState.Loading
            val result = runCatching {
                repository.exportSearchablePDF(currentDoc, outputFile).getOrThrow()
            }
            onComplete(result)
            // Restore previous state after loading
            _uiState.value = EditUiState.Success(currentDoc)
        }
    }

    fun undo() {
        val history = _editHistory.value
        if (history.isNotEmpty()) {
            val lastAction = history.last()
            // Implement undo logic based on action type
            _editHistory.value = history.dropLast(1)
        }
    }
}