package com.jascanner.presentation.screens.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.editor.DocumentEditor
import com.jascanner.repository.DocumentRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class EditorUiState(
    val isLoading: Boolean = true,
    val displayedBitmap: Bitmap? = null,
    val error: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val documentEditor: DocumentEditor,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var totalRotation = 0f
    private var docId: Long = 0L

    init {
        // Correctly get the docId from SavedStateHandle.
        savedStateHandle.get<String>("docId")?.toLongOrNull()?.let {
            docId = it
            loadDocument(docId)
        } ?: run {
            _uiState.value = EditorUiState(isLoading = false, error = "Invalid Document ID")
        }
    }

    private fun loadDocument(docId: Long) {
        viewModelScope.launch {
            _uiState.value = EditorUiState(isLoading = true)
            val document = documentRepository.getDocumentById(docId)
            if (document == null) {
                _uiState.value = EditorUiState(isLoading = false, error = "Document not found")
                return@launch
            }

            val file = File(document.filePath)
            if (!file.exists()) {
                _uiState.value = EditorUiState(isLoading = false, error = "File not found")
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    PDDocument.load(file).use { pdfDocument ->
                        val renderer = PDFRenderer(pdfDocument)
                        val bitmap = renderer.renderImageWithDPI(0, 150f)
                        originalBitmap = bitmap
                        _uiState.value = EditorUiState(isLoading = false, displayedBitmap = bitmap)
                    }
                } catch (e: Exception) {
                    _uiState.value = EditorUiState(isLoading = false, error = "Failed to load PDF page.")
                }
            }
        }
    }

    fun rotate(degrees: Float) {
        val bmp = originalBitmap
        if (bmp == null) {
            _uiState.value = _uiState.value.copy(error = "No image to rotate.")
            return
        }

        totalRotation = (totalRotation + degrees) % 360f

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val matrix = Matrix().apply { postRotate(totalRotation) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height, matrix, true
                )
                _uiState.value = _uiState.value.copy(displayedBitmap = rotatedBitmap, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to rotate image.")
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val document = documentRepository.getDocumentById(docId)
            if (document == null) {
                _uiState.value = _uiState.value.copy(error = "Cannot find document to save.")
                return@launch
            }

            if (totalRotation == 0f) {
                // No need to save if no changes were made.
                return@launch
            }

            val inputFile = File(document.filePath)
            val outputFile = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_edited.pdf")

            val success = documentEditor.saveRotation(
                inputFile = inputFile,
                outputFile = outputFile,
                rotation = totalRotation,
                pageNum = 1 // Page numbers are 1-based in iText
            )

            if (success) {
                // In a real app, you would update the document entry in the database here.
                // For now, we'll just clear the error state and let the user navigate back.
                _uiState.value = _uiState.value.copy(error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = "Failed to save the rotated PDF.")
            }
        }
    }
}