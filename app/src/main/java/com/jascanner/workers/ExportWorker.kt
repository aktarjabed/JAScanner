package com.jascanner.workers

import android.content.Context
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jascanner.export.ExportManager
import com.jascanner.repository.DocumentRepository
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.security.LTVSignatureManager
import com.jascanner.utils.FileManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File
import java.security.cert.X509Certificate

@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val pdfGenerator: PDFGenerator,
    private val signatureManager: LTVSignatureManager,
    private val exportManager: ExportManager,
    private val fileManager: FileManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_EXPORT_TYPE = "export_type"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_SIGN_WITH_LTV = "sign_with_ltv"
        const val KEY_TSA_URL = "tsa_url"
        const val KEY_CERTIFICATE_PATH = "certificate_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_SUCCESS = "success"
        
        const val EXPORT_TYPE_PDF = "pdf"
        const val EXPORT_TYPE_PDF_A = "pdf_a"
        const val EXPORT_TYPE_TEXT = "text"
        const val EXPORT_TYPE_IMAGE = "image"
    }

    override suspend fun doWork(): Result {
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, -1L)
        val exportType = inputData.getString(KEY_EXPORT_TYPE) ?: EXPORT_TYPE_PDF_A
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)
        val signWithLTV = inputData.getBoolean(KEY_SIGN_WITH_LTV, false)
        val tsaUrl = inputData.getString(KEY_TSA_URL) ?: "http://timestamp.sectigo.com"
        val certificatePath = inputData.getString(KEY_CERTIFICATE_PATH)

        if (documentId == -1L || outputPath == null) {
            return Result.failure(
                workDataOf(KEY_ERROR to "Invalid input parameters")
            )
        }

        return try {
            // Set initial progress
            setProgress(workDataOf(KEY_PROGRESS to 10))

            // Get document from database
            val document = documentRepository.getDocumentById(documentId)
                .kotlinx.coroutines.flow.first()
                ?: return Result.failure(workDataOf(KEY_ERROR to "Document not found"))

            setProgress(workDataOf(KEY_PROGRESS to 20))

            val outputFile = File(outputPath)
            fileManager.ensureDir(outputFile.parent ?: "")

            when (exportType) {
                EXPORT_TYPE_PDF_A -> exportToPDFA(document, outputFile, signWithLTV, tsaUrl, certificatePath)
                EXPORT_TYPE_PDF -> exportToPDF(document, outputFile)
                EXPORT_TYPE_TEXT -> exportToText(document, outputFile)
                EXPORT_TYPE_IMAGE -> exportToImage(document, outputFile)
                else -> return Result.failure(workDataOf(KEY_ERROR to "Unknown export type"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Export worker failed")
            Result.failure(
                workDataOf(KEY_ERROR to (e.message ?: "Export failed"))
            )
        }
    }

    private suspend fun exportToPDFA(
        document: com.jascanner.data.entities.DocumentEntity,
        outputFile: File,
        signWithLTV: Boolean,
        tsaUrl: String,
        certificatePath: String?
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 30))

        // Load original document
        val originalFile = File(document.filePath)
        if (!originalFile.exists()) {
            return Result.failure(workDataOf(KEY_ERROR to "Original file not found"))
        }

        setProgress(workDataOf(KEY_PROGRESS to 40))

        // Convert to PDF/A
        val success = if (originalFile.extension.lowercase() == "pdf") {
            pdfGenerator.convertLegacyToPDFA(originalFile, outputFile)
        } else {
            // If it's an image, create new PDF/A
            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
            if (bitmap != null) {
                pdfGenerator.generate(
                    images = listOf(bitmap),
                    ocrText = listOf(document.textContent),
                    outputFile = outputFile,
                    options = PDFGenerator.Options(
                        format = PDFGenerator.Format.PDF_A_2U,
                        title = document.title,
                        embedOCR = true,
                        compressImages = true
                    )
                )
            } else {
                false
            }
        }

        if (!success) {
            return Result.failure(workDataOf(KEY_ERROR to "PDF/A conversion failed"))
        }

        setProgress(workDataOf(KEY_PROGRESS to 70))

        // Add LTV signature if requested
        if (signWithLTV && certificatePath != null) {
            val certificateFile = File(certificatePath)
            if (certificateFile.exists()) {
                try {
                    // Load certificate chain (simplified - in practice, you'd parse P12/PFX)
                    val certificateChain = loadCertificateChain(certificateFile)
                    
                    val tempFile = File(outputFile.parent, outputFile.nameWithoutExtension + "_temp.pdf")
                    val signed = signatureManager.signWithLTV(
                        outputFile, 
                        tempFile, 
                        certificateChain, 
                        LTVSignatureManager.TSAConfig(tsaUrl)
                    )
                    
                    if (signed) {
                        outputFile.delete()
                        tempFile.renameTo(outputFile)
                    } else {
                        tempFile.delete()
                        Timber.w("LTV signing failed, but PDF/A export succeeded")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "LTV signing failed")
                    // Continue with unsigned PDF/A
                }
            }
        }

        setProgress(workDataOf(KEY_PROGRESS to 100))

        return Result.success(
            workDataOf(
                KEY_SUCCESS to true,
                KEY_OUTPUT_PATH to outputFile.absolutePath
            )
        )
    }

    private suspend fun exportToPDF(
        document: com.jascanner.data.entities.DocumentEntity,
        outputFile: File
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 50))

        val success = exportManager.exportToPdf(document.textContent, outputFile)

        setProgress(workDataOf(KEY_PROGRESS to 100))

        return if (success) {
            Result.success(
                workDataOf(
                    KEY_SUCCESS to true,
                    KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
        } else {
            Result.failure(workDataOf(KEY_ERROR to "PDF export failed"))
        }
    }

    private suspend fun exportToText(
        document: com.jascanner.data.entities.DocumentEntity,
        outputFile: File
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 50))

        return try {
            outputFile.writeText(document.textContent)
            setProgress(workDataOf(KEY_PROGRESS to 100))
            
            Result.success(
                workDataOf(
                    KEY_SUCCESS to true,
                    KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to "Text export failed: ${e.message}"))
        }
    }

    private suspend fun exportToImage(
        document: com.jascanner.data.entities.DocumentEntity,
        outputFile: File
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 30))

        return try {
            val originalFile = File(document.filePath)
            if (!originalFile.exists()) {
                return Result.failure(workDataOf(KEY_ERROR to "Original file not found"))
            }

            setProgress(workDataOf(KEY_PROGRESS to 70))

            // Copy or convert the original file
            if (originalFile.extension.lowercase() in listOf("jpg", "jpeg", "png", "bmp")) {
                originalFile.copyTo(outputFile, overwrite = true)
            } else {
                return Result.failure(workDataOf(KEY_ERROR to "Cannot export non-image to image format"))
            }

            setProgress(workDataOf(KEY_PROGRESS to 100))

            Result.success(
                workDataOf(
                    KEY_SUCCESS to true,
                    KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to "Image export failed: ${e.message}"))
        }
    }

    private fun loadCertificateChain(certificateFile: File): Array<X509Certificate> {
        // Simplified certificate loading - in practice, you'd parse P12/PFX files
        // This is a placeholder implementation
        return emptyArray()
    }
}