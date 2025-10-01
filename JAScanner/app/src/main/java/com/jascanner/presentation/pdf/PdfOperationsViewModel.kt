package com.jascanner.presentation.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.repository.PdfOperationsRepository
import com.jascanner.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PdfOperationsViewModel @Inject constructor(
    private val pdfRepository: PdfOperationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfOperationsUiState>(PdfOperationsUiState.Idle)
    val uiState: StateFlow<PdfOperationsUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow<PdfOperationProgress?>(null)
    val progress: StateFlow<PdfOperationProgress?> = _progress.asStateFlow()

    fun mergePdfs(
        pdfFiles: List<File>,
        outputName: String,
        options: MergeOptions = MergeOptions()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PdfOperationsUiState.Processing("Merging PDFs...")

                // Validate inputs
                if (pdfFiles.isEmpty()) {
                    throw IllegalArgumentException("No PDF files selected")
                }

                if (pdfFiles.size < 2) {
                    throw IllegalArgumentException("At least 2 PDF files required for merging")
                }

                pdfFiles.forEach { file ->
                    if (!file.exists()) {
                        throw IllegalArgumentException("File not found: ${file.name}")
                    }
                    if (!file.name.endsWith(".pdf", ignoreCase = true)) {
                        throw IllegalArgumentException("Invalid file type: ${file.name}")
                    }
                }

                // Perform merge with progress tracking
                val result = pdfRepository.mergePdfs(
                    pdfFiles = pdfFiles,
                    outputName = outputName,
                    options = options,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                when (result) {
                    is PdfOperationResult.Success -> {
                        _uiState.value = PdfOperationsUiState.Success(
                            message = "PDFs merged successfully",
                            outputFile = result.file
                        )
                        Timber.d("PDFs merged: ${result.file.absolutePath}")
                    }
                    is PdfOperationResult.Error -> {
                        _uiState.value = PdfOperationsUiState.Error(
                            message = "Failed to merge PDFs: ${result.message}",
                            error = result.exception,
                            recoverable = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error merging PDFs")
                _uiState.value = PdfOperationsUiState.Error(
                    message = "Error: ${e.message}",
                    error = e,
                    recoverable = true
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun splitPdf(
        pdfFile: File,
        splitOptions: SplitOptions
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PdfOperationsUiState.Processing("Splitting PDF...")

                // Validate input
                if (!pdfFile.exists()) {
                    throw IllegalArgumentException("PDF file not found")
                }

                val result = pdfRepository.splitPdf(
                    pdfFile = pdfFile,
                    splitOptions = splitOptions,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                when (result) {
                    is PdfOperationResult.SuccessMultiple -> {
                        _uiState.value = PdfOperationsUiState.SuccessMultiple(
                            message = "PDF split into ${result.files.size} files",
                            outputFiles = result.files
                        )
                        Timber.d("PDF split into ${result.files.size} files")
                    }
                    is PdfOperationResult.Error -> {
                        _uiState.value = PdfOperationsUiState.Error(
                            message = "Failed to split PDF: ${result.message}",
                            error = result.exception,
                            recoverable = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error splitting PDF")
                _uiState.value = PdfOperationsUiState.Error(
                    message = "Error: ${e.message}",
                    error = e,
                    recoverable = true
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun extractPages(
        pdfFile: File,
        pageNumbers: List<Int>,
        outputName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PdfOperationsUiState.Processing("Extracting pages...")

                // Validate input
                if (pageNumbers.isEmpty()) {
                    throw IllegalArgumentException("No pages selected for extraction")
                }

                val result = pdfRepository.extractPages(
                    pdfFile = pdfFile,
                    pageNumbers = pageNumbers,
                    outputName = outputName,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                when (result) {
                    is PdfOperationResult.Success -> {
                        _uiState.value = PdfOperationsUiState.Success(
                            message = "Extracted ${pageNumbers.size} pages",
                            outputFile = result.file
                        )
                    }
                    is PdfOperationResult.Error -> {
                        _uiState.value = PdfOperationsUiState.Error(
                            message = "Failed to extract pages: ${result.message}",
                            error = result.exception,
                            recoverable = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error extracting pages")
                _uiState.value = PdfOperationsUiState.Error(
                    message = "Error: ${e.message}",
                    error = e,
                    recoverable = true
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun addWatermark(
        pdfFile: File,
        watermark: Watermark,
        outputName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PdfOperationsUiState.Processing("Adding watermark...")

                val result = pdfRepository.addWatermark(
                    pdfFile = pdfFile,
                    watermark = watermark,
                    outputName = outputName,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                when (result) {
                    is PdfOperationResult.Success -> {
                        _uiState.value = PdfOperationsUiState.Success(
                            message = "Watermark added successfully",
                            outputFile = result.file
                        )
                    }
                    is PdfOperationResult.Error -> {
                        _uiState.value = PdfOperationsUiState.Error(
                            message = "Failed to add watermark: ${result.message}",
                            error = result.exception,
                            recoverable = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error adding watermark")
                _uiState.value = PdfOperationsUiState.Error(
                    message = "Error: ${e.message}",
                    error = e,
                    recoverable = true
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun applyRedactions(
        pdfFile: File,
        redactions: List<RedactionArea>,
        outputName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PdfOperationsUiState.Processing("Applying redactions...")

                if (redactions.isEmpty()) {
                    throw IllegalArgumentException("No redactions to apply")
                }

                val result = pdfRepository.applyRedactions(
                    pdfFile = pdfFile,
                    redactions = redactions,
                    outputName = outputName,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                when (result) {
                    is PdfOperationResult.Success -> {
                        _uiState.value = PdfOperationsUiState.Success(
                            message = "Redactions applied to ${redactions.size} areas",
                            outputFile = result.file
                        )
                    }
                    is PdfOperationResult.Error -> {
                        _uiState.value = PdfOperationsUiState.Error(
                            message = "Failed to apply redactions: ${result.message}",
                            error = result.exception,
                            recoverable = false
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error applying redactions")
                _uiState.value = PdfOperationsUiState.Error(
                    message = "Error: ${e.message}",
                    error = e,
                    recoverable = false
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun resetState() {
        _uiState.value = PdfOperationsUiState.Idle
        _progress.value = null
    }
}

sealed class PdfOperationsUiState {
    object Idle : PdfOperationsUiState()
    data class Processing(val message: String) : PdfOperationsUiState()
    data class Success(val message: String, val outputFile: File) : PdfOperationsUiState()
    data class SuccessMultiple(val message: String, val outputFiles: List<File>) : PdfOperationsUiState()
    data class Error(val message: String, val error: Throwable, val recoverable: Boolean) : PdfOperationsUiState()
}