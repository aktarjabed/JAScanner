package com.jascanner.data.repository

import android.content.Context
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Paragraph
import com.jascanner.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class PdfOperationResult {
    data class Success(val file: File) : PdfOperationResult()
    data class SuccessMultiple(val files: List<File>) : PdfOperationResult()
    data class Error(val message: String, val exception: Throwable) : PdfOperationResult()
}

data class PdfOperationProgress(
    val current: Int,
    val total: Int,
    val message: String
)

@Singleton
class PdfOperationsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun mergePdfs(
        pdfFiles: List<File>,
        outputName: String,
        options: MergeOptions,
        progressCallback: (PdfOperationProgress) -> Unit
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        val outputFile = File(context.filesDir, sanitizeFilename(outputName))
        var writer: PdfWriter? = null
        var mergedPdf: PdfDocument? = null
        try {
            writer = PdfWriter(outputFile)
            mergedPdf = PdfDocument(writer)
            val merger = com.itextpdf.kernel.utils.PdfMerger(mergedPdf)

            pdfFiles.forEachIndexed { index, file ->
                var sourcePdf: PdfDocument? = null
                try {
                    progressCallback(
                        PdfOperationProgress(
                            current = index + 1,
                            total = pdfFiles.size,
                            message = "Merging ${file.name}..."
                        )
                    )

                    val reader = PdfReader(file)
                    sourcePdf = PdfDocument(reader)

                    val pageNumbers = if (options.specificPages.isEmpty()) {
                        (1..sourcePdf.numberOfPages).toList()
                    } else {
                        options.specificPages
                    }

                    pageNumbers.forEach { pageNum ->
                        if (pageNum in 1..sourcePdf.numberOfPages) {
                            merger.merge(sourcePdf, pageNum, pageNum)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error merging file: ${file.name}")
                    if (!options.continueOnError) throw e
                } finally {
                    sourcePdf?.close()
                }
            }

            val info = mergedPdf.documentInfo
            info.title = outputName
            info.creator = "JAScanner"

            PdfOperationResult.Success(outputFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to merge PDFs")
            outputFile.delete()
            PdfOperationResult.Error("Merge failed: ${e.message}", e)
        } finally {
            try {
                mergedPdf?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing PDF document")
            }
        }
    }

    suspend fun splitPdf(
        pdfFile: File,
        splitOptions: SplitOptions,
        progressCallback: (PdfOperationProgress) -> Unit
    ): PdfOperationResult {
        // Implementation with proper resource handling would be added here
        return PdfOperationResult.Error("Not yet implemented", NotImplementedError())
    }

    suspend fun extractPages(
        pdfFile: File,
        pageNumbers: List<Int>,
        outputName: String,
        progressCallback: (PdfOperationProgress) -> Unit
    ): PdfOperationResult {
        // Implementation with proper resource handling would be added here
        return PdfOperationResult.Error("Not yet implemented", NotImplementedError())
    }

    suspend fun addWatermark(
        pdfFile: File,
        watermark: Watermark,
        outputName: String,
        progressCallback: (PdfOperationProgress) -> Unit
    ): PdfOperationResult {
        // Implementation with proper resource handling would be added here
        return PdfOperationResult.Error("Not yet implemented", NotImplementedError())
    }

    suspend fun applyRedactions(
        pdfFile: File,
        redactions: List<RedactionArea>,
        outputName: String,
        progressCallback: (PdfOperationProgress) -> Unit
    ): PdfOperationResult {
        // Implementation with proper resource handling would be added here
        return PdfOperationResult.Error("Not yet implemented", NotImplementedError())
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            .take(255)
            .ifBlank { "output_${System.currentTimeMillis()}.pdf" }
    }
}