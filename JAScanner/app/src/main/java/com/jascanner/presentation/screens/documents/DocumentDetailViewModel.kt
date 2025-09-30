package com.jascanner.presentation.screens.documents

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.export.ExportFormat
import com.jascanner.export.ImageExporter
import com.jascanner.export.QualityProfile
import com.jascanner.repository.DocumentRepository
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.security.LTVSignatureManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val pdfGenerator: PDFGenerator,
    private val signatureManager: LTVSignatureManager,
    private val imageExporter: ImageExporter
): ViewModel() {

    private val _ui = MutableStateFlow(DocumentDetailUiState())
    val ui: StateFlow<DocumentDetailUiState> = _ui.asStateFlow()

    fun load(docId: Long) = viewModelScope.launch {
        documentRepository.getDocumentById(docId).collect { doc ->
            _ui.value = _ui.value.copy(document = doc, isLoading = false, error = if (doc == null) "Not found" else null)
        }
    }

    fun exportPDFA() = viewModelScope.launch {
        val doc = _ui.value.document ?: return@launch
        val inFile = File(doc.filePath)
        val out = File(inFile.parentFile, "${inFile.nameWithoutExtension}_pdfa.pdf")
        val ok = pdfGenerator.convertLegacyToPDFA(inFile, out)
        _ui.value = if (ok) _ui.value.copy(message = "PDF/A: ${out.name}", exportedFile = out) else _ui.value.copy(error = "PDF/A export failed")
    }

    fun addLTVSignature(chain: Array<java.security.cert.X509Certificate>, tsaUrl: String) = viewModelScope.launch {
        val doc = _ui.value.document ?: return@launch
        val inFile = File(doc.filePath)
        val out = File(inFile.parentFile, "${inFile.nameWithoutExtension}_signed.pdf")
        val ok = signatureManager.signWithLTV(inFile, out, chain, LTVSignatureManager.TSAConfig(tsaUrl))
        _ui.value = if (ok) _ui.value.copy(message = "Signed: ${out.name}", exportedFile = out) else _ui.value.copy(error = "Sign failed")
    }

    fun verify() = viewModelScope.launch {
        val doc = _ui.value.document ?: return@launch
        val file = File(doc.filePath)
        val (valid, msg) = signatureManager.verifyWithLTV(file)
        _ui.value = _ui.value.copy(signatureInfo = msg, message = if (valid) "✓ Valid & LTV" else "⚠ Not valid")
    }

    fun exportImage(format: ExportFormat, quality: QualityProfile) = viewModelScope.launch {
        val doc = _ui.value.document ?: return@launch
        val inFile = File(doc.filePath)

        if (!inFile.exists()) {
            _ui.value = _ui.value.copy(error = "Source file not found")
            return@launch
        }

        try {
            PDDocument.load(inFile).use { pdfDocument ->
                val renderer = PDFRenderer(pdfDocument)
                val bitmap = renderer.renderImage(0, 1.0f) // Render first page at 1x scale

                val success = imageExporter.save(
                    bitmap = bitmap,
                    format = format,
                    quality = quality,
                    fileName = inFile.nameWithoutExtension
                )

                if (success) {
                    _ui.value = _ui.value.copy(message = "${format.name} exported to Pictures folder")
                } else {
                    _ui.value = _ui.value.copy(error = "Failed to save ${format.name}")
                }
            }
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(error = "Failed to export ${format.name}: ${e.message}")
        }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null, exportedFile = null) }
    fun clearError() { _ui.value = _ui.value.copy(error = null) }
}

data class DocumentDetailUiState(
    val isLoading: Boolean = false,
    val document: com.jascanner.data.entities.DocumentEntity? = null,
    val error: String? = null,
    val message: String? = null,
    val exportedFile: File? = null,
    val signatureInfo: String? = null
)

