package com.jascanner.pdf

import android.content.Context
import android.graphics.BitmapFactory
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.jascanner.core.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobustPDFGenerator @Inject constructor(
    private val context: Context,
    private val errorHandler: ErrorHandler
) {

    sealed class Result {
        data class Success(val file: File) : Result()
        data class Error(val exception: Exception) : Result()
    }

    suspend fun generatePdfFromImages(imagePaths: List<String>, title: String): Result {
        return withContext(Dispatchers.IO) {
            try {
                val outputFile = createPdfFile(title)
                val writer = PdfWriter(FileOutputStream(outputFile))
                val pdfDocument = PdfDocument(writer)
                val document = Document(pdfDocument)

                for (imagePath in imagePaths) {
                    try {
                        val imageFile = File(imagePath)
                        if (imageFile.exists()) {
                            // Load bitmap bounds to get dimensions without loading the full image into memory
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(imagePath, options)

                            val imageData = ImageDataFactory.create(imagePath)
                            val image = Image(imageData)

                            // Scale image to fit page
                            image.setAutoScale(true)

                            document.add(image)
                        } else {
                            Timber.w("Image file not found, skipping: %s", imagePath)
                        }
                    } catch (e: Exception) {
                        val errorMessage = "Failed to add image to PDF: $imagePath"
                        Timber.e(e, errorMessage)
                        // Continue to next image
                    }
                }

                document.close()
                Timber.d("PDF generated successfully: %s", outputFile.absolutePath)
                Result.Success(outputFile)
            } catch (e: Exception) {
                val errorMessage = "Failed to generate PDF"
                Timber.e(e, errorMessage)
                errorHandler.recordException(e, errorMessage)
                Result.Error(e)
            }
        }
    }

    private fun createPdfFile(title: String): File {
        val pdfDir = File(context.cacheDir, "pdfs")
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        return File(pdfDir, "$sanitizedTitle-${System.currentTimeMillis()}.pdf")
    }
}