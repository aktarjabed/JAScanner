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

class EditViewModel(
    private val repository: EditRepository
) : ViewModel() {

    private val _document = MutableStateFlow<EditableDocument?>(null)
    val document: StateFlow<EditableDocument?> = _document.asStateFlow()

    private val _editHistory = MutableStateFlow<List<EditAction>>(emptyList())
    val editHistory: StateFlow<List<EditAction>> = _editHistory.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun performOCR(pageIndex: Int, bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val textBlocks = repository.performOCR(bitmap)
                _document.value = _document.value?.let { doc ->
                    val updatedPages = doc.pages.toMutableList()
                    updatedPages[pageIndex] = updatedPages[pageIndex].copy(ocrTextLayer = textBlocks)
                    doc.copy(pages = updatedPages, modifiedAt = System.currentTimeMillis())
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun cropPage(pageIndex: Int, bitmap: Bitmap, cropRect: RectF) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val croppedBitmap = repository.cropBitmap(bitmap, cropRect)
                val savedUri = repository.saveBitmap(
                    croppedBitmap,
                    "cropped_${System.currentTimeMillis()}.png"
                )

                _document.value = _document.value?.let { doc ->
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
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun addAnnotations(pageIndex: Int, annotations: List<Annotation>) {
        _document.value = _document.value?.let { doc ->
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
    }

    fun exportPDF(outputFile: File, onComplete: (Result<File>) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                _document.value?.let { doc ->
                    val result = repository.exportSearchablePDF(doc, outputFile)
                    onComplete(result)
                }
            } finally {
                _isProcessing.value = false
            }
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