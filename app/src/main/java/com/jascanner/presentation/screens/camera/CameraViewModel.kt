package com.jascanner.presentation.screens.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.repository.DocumentRepository
import com.jascanner.scanner.camera.CameraController
import com.jascanner.scanner.ocr.OCRProcessor
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.utils.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val ocrProcessor: OCRProcessor,
    private val pdfGenerator: PDFGenerator,
    private val documentRepository: DocumentRepository,
    private val fileManager: FileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        initializeCamera()
    }

    private fun initializeCamera() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInitializing = true)
            
            val initialized = cameraController.initialize()
            if (initialized) {
                // Observe camera state
                cameraController.state.collect { cameraState ->
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        cameraReady = cameraState.isInitialized,
                        hasFlash = cameraState.hasFlash,
                        isFlashOn = cameraState.flashEnabled,
                        isCapturing = cameraState.isCapturing,
                        error = cameraState.error
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isInitializing = false,
                    error = "Failed to initialize camera"
                )
            }
        }
    }

    fun captureImage() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isCapturing = true,
                    isProcessing = false,
                    error = null
                )

                val outputFile = fileManager.createTempFile("capture_", ".jpg")
                val result = cameraController.captureImage(outputFile)

                if (result.success && result.bitmap != null) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isProcessing = true,
                        capturedImage = result.bitmap
                    )

                    // Process the captured image
                    processImage(result.bitmap, outputFile)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        error = result.error ?: "Capture failed"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Image capture failed")
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    error = e.message
                )
            }
        }
    }

    fun captureBurst() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isCapturing = true,
                    isBurstMode = true,
                    error = null
                )

                val outputDir = File(fileManager.getAppDir(), "burst_${System.currentTimeMillis()}")
                fileManager.ensureDir(outputDir.absolutePath)

                val results = cameraController.captureBurst(outputDir, 3)
                val successfulResults = results.filter { it.success && it.bitmap != null }

                if (successfulResults.isNotEmpty()) {
                    // Select the best image from burst
                    val bestResult = selectBestImage(successfulResults)
                    
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isBurstMode = false,
                        isProcessing = true,
                        capturedImage = bestResult.bitmap,
                        burstCount = successfulResults.size
                    )

                    // Process the best image
                    bestResult.file?.let { file ->
                        processImage(bestResult.bitmap!!, file)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isBurstMode = false,
                        error = "Burst capture failed"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Burst capture failed")
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    isBurstMode = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap, imageFile: File) {
        try {
            // OCR processing
            val ocrResult = ocrProcessor.processImage(bitmap, enhanceWithAI = true)
            
            if (ocrResult.success) {
                _uiState.value = _uiState.value.copy(
                    ocrText = ocrResult.text,
                    ocrConfidence = ocrResult.confidence,
                    processingTimeMs = ocrResult.processingTimeMs
                )

                // Generate PDF
                val pdfFile = File(
                    imageFile.parent,
                    imageFile.nameWithoutExtension + "_scan.pdf"
                )

                val pdfGenerated = pdfGenerator.generate(
                    images = listOf(bitmap),
                    ocrText = listOf(ocrResult.text),
                    outputFile = pdfFile,
                    options = PDFGenerator.Options(
                        format = PDFGenerator.Format.PDF_A_2U,
                        embedOCR = true,
                        compressImages = true
                    )
                )

                if (pdfGenerated) {
                    // Save to database
                    val docId = documentRepository.insertDocument(
                        title = "Scan ${System.currentTimeMillis()}",
                        textContent = ocrResult.text,
                        filePath = pdfFile.absolutePath
                    )

                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        documentSaved = true,
                        savedDocumentId = docId,
                        message = "Document saved successfully"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Failed to generate PDF"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = ocrResult.error ?: "OCR processing failed"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Image processing failed")
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = e.message
            )
        }
    }

    private fun selectBestImage(results: List<CameraController.CaptureResult>): CameraController.CaptureResult {
        // Select the image with the best focus quality
        return results.maxByOrNull { result ->
            result.bitmap?.let { bitmap ->
                val focusResult = cameraController.evaluateFocus(bitmap)
                focusResult.sharpness * focusResult.confidence
            } ?: 0.0
        } ?: results.first()
    }

    fun toggleFlash() {
        cameraController.toggleFlash()
    }

    fun switchCamera() {
        cameraController.switchCamera()
    }

    fun setZoom(zoomRatio: Float) {
        cameraController.setZoom(zoomRatio)
    }

    fun retakePhoto() {
        _uiState.value = _uiState.value.copy(
            capturedImage = null,
            ocrText = "",
            ocrConfidence = 0f,
            documentSaved = false,
            savedDocumentId = 0L,
            isProcessing = false,
            error = null,
            message = null
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.shutdown()
    }
}

data class CameraUiState(
    val isInitializing: Boolean = false,
    val cameraReady: Boolean = false,
    val hasFlash: Boolean = false,
    val isFlashOn: Boolean = false,
    val isCapturing: Boolean = false,
    val isBurstMode: Boolean = false,
    val isProcessing: Boolean = false,
    val capturedImage: Bitmap? = null,
    val ocrText: String = "",
    val ocrConfidence: Float = 0f,
    val processingTimeMs: Long = 0L,
    val burstCount: Int = 0,
    val documentSaved: Boolean = false,
    val savedDocumentId: Long = 0L,
    val error: String? = null,
    val message: String? = null
)