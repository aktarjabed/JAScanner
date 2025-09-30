package com.jascanner.presentation.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.repository.DocumentRepository
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.security.LTVSignatureManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val pdfGenerator: PDFGenerator,
    private val signatureManager: LTVSignatureManager
): ViewModel() {

    private val _ui = MutableStateFlow(DocumentDetailUiState())
    val ui: StateFlow<DocumentDetailUiState> = _ui.asStateFlow()

    fun load(docId: Long) = viewModelScope.launch {
        documentRepository.getDocumentById(docId).collect { doc ->
            if (doc != null) {
                val file = File(doc.filePath)
                _ui.value = _ui.value.copy(
                    document = doc,
                    isLoading = false,
                    error = null,
                    pageCount = 1, // Assuming one image per document for now
                    originalSize = if (file.exists()) file.length() else 0L
                )
            } else {
                _ui.value = _ui.value.copy(
                    document = null,
                    isLoading = false,
                    error = "Not found"
                )
            }
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

    fun clearMessage() { _ui.value = _ui.value.copy(message = null, exportedFile = null) }
    fun clearError() { _ui.value = _ui.value.copy(error = null) }
}

data class DocumentDetailUiState(
    val isLoading: Boolean = false,
    val document: com.jascanner.data.entities.DocumentEntity? = null,
    val pageCount: Int = 0,
    val originalSize: Long = 0,
    val error: String? = null,
    val message: String? = null,
    val exportedFile: File? = null,
    val signatureInfo: String? = null
)

