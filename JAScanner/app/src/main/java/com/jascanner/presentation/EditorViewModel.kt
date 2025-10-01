package com.jascanner.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.domain.model.EditableDocument
import com.jascanner.editor.UndoRedoManager
import com.jascanner.utils.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class EditorTool {
    CROP, ROTATE, FILTER, ADJUST, ANNOTATE, SIGN, TEXT
}

sealed class EditorUiState {
    object Loading : EditorUiState()
    data class Success(val document: EditableDocument) : EditorUiState()
    data class Error(val message: String, val error: Throwable) : EditorUiState()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<EditableDocument?>(null)
    val currentDocument: StateFlow<EditableDocument?> = _currentDocument.asStateFlow()

    private val _selectedTool = MutableStateFlow<EditorTool?>(null)
    val selectedTool: StateFlow<EditorTool?> = _selectedTool.asStateFlow()

    private val undoRedoManager = UndoRedoManager(maxStackSize = 50)

    val canUndo: StateFlow<Boolean> = flow {
        while (true) {
            emit(undoRedoManager.canUndo())
            delay(100)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val canRedo: StateFlow<Boolean> = flow {
        while (true) {
            emit(undoRedoManager.canRedo())
            delay(100)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private var backgroundJobs = mutableListOf<Job>()

    fun loadDocument(documentId: String) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                val document = documentRepository.getEditableDocument(documentId)
                if (document != null) {
                    _currentDocument.value = document
                    _uiState.value = EditorUiState.Success(document)
                } else {
                    _uiState.value = EditorUiState.Error("Document not found", Exception("Document not found"))
                }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error("Failed to load document", e)
            }
        }
    }

    fun selectTool(tool: EditorTool) {
        try {
            _selectedTool.value = tool
        } catch (e: Exception) {
            Timber.e(e, "Failed to select tool")
            _uiState.value = EditorUiState.Error(
                message = "Failed to select tool: ${e.message}",
                error = e
            )
        }
    }

    fun undo() {
        viewModelScope.launch {
            currentDocument.value?.let { doc ->
                when (val result = undoRedoManager.undo(doc)) {
                    is UndoRedoManager.UndoRedoResult.Success -> {
                        _currentDocument.value = result.document
                        // showMessage("Undone: ${result.operationName}")
                    }
                    is UndoRedoManager.UndoRedoResult.NoStateAvailable -> {
                        // showMessage("Nothing to undo")
                    }
                }
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            when (val result = undoRedoManager.redo()) {
                is UndoRedoManager.UndoRedoResult.Success -> {
                    _currentDocument.value = result.document
                    // showMessage("Redone: ${result.operationName}")
                }
                is UndoRedoManager.UndoRedoResult.NoStateAvailable -> {
                    // showMessage("Nothing to redo")
                }
            }
        }
    }

    fun saveCurrentState() {
        viewModelScope.launch {
            try {
                currentDocument.value?.let { doc ->
                    documentRepository.saveEditableDocument(doc)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save current state")
            }
        }
    }

    fun pauseBackgroundOperations() {
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.clear()
    }

    fun resumeBackgroundOperations() {
        // Resume any necessary background work
    }

    fun cleanup() {
        pauseBackgroundOperations()
        currentDocument.value?.pages?.forEach { page ->
            BitmapUtils.recycleSafely(page.originalBitmap)
            BitmapUtils.recycleSafely(page.processedBitmap)
            BitmapUtils.recycleSafely(page.thumbnail)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}