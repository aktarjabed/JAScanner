package com.jascanner.compression.presentation

import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.compression.data.repository.MultiFormatCompressor
import com.jascanner.compression.domain.model.*
import com.jascanner.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CompressionViewModel @Inject constructor(
    private val compressor: MultiFormatCompressor,
    private val documentRepository: DocumentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _compressionState = MutableStateFlow<CompressionState>(CompressionState.Idle)
    val compressionState: StateFlow<CompressionState> = _compressionState

    fun compressAndExport(
        docId: Long,
        settings: CompressionSettings,
        formatSettings: FormatSpecificSettings
    ) {
        viewModelScope.launch {
            _compressionState.value = CompressionState.Processing(0)

            try {
                val doc = documentRepository.getDocumentById(docId).first()
                if (doc == null) {
                    _compressionState.value = CompressionState.Error("Document not found")
                    return@launch
                }

                val bitmap = BitmapFactory.decodeFile(doc.filePath)
                if (bitmap == null) {
                    _compressionState.value = CompressionState.Error("Failed to load image")
                    return@launch
                }

                val outputFile = File(context.cacheDir, "compressed.${settings.outputFormat.extension}")

                val result = compressor.compressAndExport(
                    listOf(bitmap), settings, outputFile, formatSettings
                )

                result.fold(
                    onSuccess = { compressionResult ->
                        _compressionState.value = CompressionState.Success(compressionResult)
                    },
                    onFailure = { error ->
                        _compressionState.value = CompressionState.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                _compressionState.value = CompressionState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class CompressionState {
    object Idle : CompressionState()
    data class Processing(val progress: Int) : CompressionState()
    data class Success(val result: CompressionResult) : CompressionState()
    data class Error(val message: String) : CompressionState()
}