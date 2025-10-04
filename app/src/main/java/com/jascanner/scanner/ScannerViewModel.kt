package com.jascanner.scanner

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.core.EnhancedErrorHandler
import com.jascanner.data.repository.EnhancedDocumentRepository
import com.jascanner.device.camera.RobustCameraController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class ScannerState(
    val documentId: Long? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val capturedBitmaps: List<Bitmap> = emptyList()
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentRepository: EnhancedDocumentRepository,
    val cameraController: RobustCameraController
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerState())
    val uiState: StateFlow<ScannerState> = _uiState

    fun captureImage(context: Context) {
        viewModelScope.launch {
            when (val result = cameraController.captureImage()) {
                is RobustCameraController.Result.Success -> {
                    processCapturedImage(result.data, application)
                }
                is RobustCameraController.Result.Error -> {
                    _uiState.value = _uiState.value.copy(error = "Failed to capture image.")
                }
            }
        }
    }

    private fun processCapturedImage(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val imageFile = saveBitmapToFile(bitmap, context)
            if (imageFile == null) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Failed to save image file")
                return@launch
            }

            var documentId = _uiState.value.documentId
            if (documentId == null) {
                when (val result = documentRepository.createDocument("New Scan")) {
                    is ErrorHandler.Result.Success -> {
                        documentId = result.data
                        _uiState.value = _uiState.value.copy(documentId = documentId)
                    }
                    is ErrorHandler.Result.Error -> {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                        return@launch
                    }
                }
            }

            if (documentId == null) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Failed to create document")
                return@launch
            }

            when (val result = documentRepository.addScanToDocument(documentId, imageFile.absolutePath)) {
                is ErrorHandler.Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        capturedBitmaps = _uiState.value.capturedBitmaps + bitmap
                    )
                }
                is ErrorHandler.Result.Error -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, context: Context): File? {
        return try {
            val directory = File(context.cacheDir, "scans")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, "scan_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            file
        } catch (e: Exception) {
            Timber.e(e, "Failed to save bitmap to file")
            null
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.release()
    }
}