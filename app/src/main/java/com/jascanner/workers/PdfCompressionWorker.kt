package com.jascanner.workers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jascanner.compression.AdvancedCompressionEngine
import com.jascanner.repository.DocumentRepository
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.utils.FileManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

@HiltWorker
class PdfCompressionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val compressionEngine: AdvancedCompressionEngine,
    private val pdfGenerator: PDFGenerator,
    private val fileManager: FileManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_COMPRESSION_LEVEL = "compression_level"
        const val KEY_MAX_WIDTH = "max_width"
        const val KEY_MAX_HEIGHT = "max_height"
        const val KEY_QUALITY = "quality"
        const val KEY_PRESERVE_METADATA = "preserve_metadata"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_SUCCESS = "success"
        const val KEY_ORIGINAL_SIZE = "original_size"
        const val KEY_COMPRESSED_SIZE = "compressed_size"
        const val KEY_COMPRESSION_RATIO = "compression_ratio"
        
        const val COMPRESSION_LEVEL_LOW = 1
        const val COMPRESSION_LEVEL_MEDIUM = 2
        const val COMPRESSION_LEVEL_HIGH = 3
        const val COMPRESSION_LEVEL_MAXIMUM = 4
    }

    override suspend fun doWork(): Result {
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, -1L)
        val compressionLevel = inputData.getInt(KEY_COMPRESSION_LEVEL, COMPRESSION_LEVEL_MEDIUM)
        val maxWidth = inputData.getInt(KEY_MAX_WIDTH, 1920)
        val maxHeight = inputData.getInt(KEY_MAX_HEIGHT, 1080)
        val quality = inputData.getInt(KEY_QUALITY, 85)
        val preserveMetadata = inputData.getBoolean(KEY_PRESERVE_METADATA, true)

        if (documentId == -1L) {
            return Result.failure(
                workDataOf(KEY_ERROR to "Invalid document ID")
            )
        }

        return try {
            // Set initial progress
            setProgress(workDataOf(KEY_PROGRESS to 5))

            // Get document from database
            val document = documentRepository.getDocumentById(documentId)
                .kotlinx.coroutines.flow.first()
                ?: return Result.failure(workDataOf(KEY_ERROR to "Document not found"))

            val originalFile = File(document.filePath)
            if (!originalFile.exists()) {
                return Result.failure(workDataOf(KEY_ERROR to "Original file not found"))
            }

            setProgress(workDataOf(KEY_PROGRESS to 10))

            val originalSize = originalFile.length()
            val compressionOptions = createCompressionOptions(compressionLevel, maxWidth, maxHeight, quality)

            when (originalFile.extension.lowercase()) {
                "pdf" -> compressPdf(document, originalFile, compressionOptions, preserveMetadata)
                "jpg", "jpeg", "png", "bmp" -> compressImage(document, originalFile, compressionOptions)
                else -> Result.failure(workDataOf(KEY_ERROR to "Unsupported file format"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Compression worker failed")
            Result.failure(
                workDataOf(KEY_ERROR to (e.message ?: "Compression failed"))
            )
        }
    }

    private suspend fun compressPdf(
        document: com.jascanner.data.entities.DocumentEntity,
        originalFile: File,
        compressionOptions: AdvancedCompressionEngine.CompressionOptions,
        preserveMetadata: Boolean
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 20))

        try {
            // Create temporary compressed file
            val tempCompressedFile = fileManager.createTempFile("compressed_", ".pdf")
            
            setProgress(workDataOf(KEY_PROGRESS to 30))

            // Extract images from PDF and compress them
            val extractedImages = extractImagesFromPdf(originalFile)
            val compressedImages = mutableListOf<Bitmap>()
            
            setProgress(workDataOf(KEY_PROGRESS to 40))

            // Compress each image
            extractedImages.forEachIndexed { index, image ->
                val tempImageFile = fileManager.createTempFile("temp_image_$index", ".jpg")
                val tempCompressedImageFile = fileManager.createTempFile("temp_compressed_$index", ".jpg")
                
                // Save original image
                tempImageFile.outputStream().use { out ->
                    image.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                
                // Compress image
                val compressionResult = compressionEngine.compressImage(
                    tempImageFile, 
                    tempCompressedImageFile, 
                    compressionOptions
                )
                
                if (compressionResult.success) {
                    val compressedBitmap = BitmapFactory.decodeFile(tempCompressedImageFile.absolutePath)
                    if (compressedBitmap != null) {
                        compressedImages.add(compressedBitmap)
                    }
                }
                
                // Clean up temp files
                tempImageFile.delete()
                tempCompressedImageFile.delete()
                
                setProgress(workDataOf(KEY_PROGRESS to (40 + (index + 1) * 30 / extractedImages.size)))
            }

            setProgress(workDataOf(KEY_PROGRESS to 70))

            // Recreate PDF with compressed images
            val pdfOptions = PDFGenerator.Options(
                title = document.title,
                embedOCR = true,
                compressImages = true
            )

            val success = pdfGenerator.generate(
                images = compressedImages,
                ocrText = listOf(document.textContent),
                outputFile = tempCompressedFile,
                options = pdfOptions
            )

            if (!success) {
                return Result.failure(workDataOf(KEY_ERROR to "Failed to recreate compressed PDF"))
            }

            setProgress(workDataOf(KEY_PROGRESS to 85))

            // Replace original file with compressed version
            val compressedSize = tempCompressedFile.length()
            val originalSize = originalFile.length()
            val compressionRatio = if (originalSize > 0) {
                (originalSize - compressedSize).toDouble() / originalSize
            } else 0.0

            originalFile.delete()
            tempCompressedFile.renameTo(originalFile)

            setProgress(workDataOf(KEY_PROGRESS to 100))

            return Result.success(
                workDataOf(
                    KEY_SUCCESS to true,
                    KEY_ORIGINAL_SIZE to originalSize,
                    KEY_COMPRESSED_SIZE to compressedSize,
                    KEY_COMPRESSION_RATIO to compressionRatio
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "PDF compression failed")
            return Result.failure(workDataOf(KEY_ERROR to "PDF compression failed: ${e.message}"))
        }
    }

    private suspend fun compressImage(
        document: com.jascanner.data.entities.DocumentEntity,
        originalFile: File,
        compressionOptions: AdvancedCompressionEngine.CompressionOptions
    ): Result {
        setProgress(workDataOf(KEY_PROGRESS to 30))

        val tempCompressedFile = fileManager.createTempFile("compressed_", ".jpg")
        
        setProgress(workDataOf(KEY_PROGRESS to 50))

        val compressionResult = compressionEngine.compressImage(
            originalFile,
            tempCompressedFile,
            compressionOptions
        )

        setProgress(workDataOf(KEY_PROGRESS to 80))

        return if (compressionResult.success) {
            // Replace original file with compressed version
            originalFile.delete()
            tempCompressedFile.renameTo(originalFile)

            setProgress(workDataOf(KEY_PROGRESS to 100))

            Result.success(
                workDataOf(
                    KEY_SUCCESS to true,
                    KEY_ORIGINAL_SIZE to compressionResult.originalSize,
                    KEY_COMPRESSED_SIZE to compressionResult.compressedSize,
                    KEY_COMPRESSION_RATIO to compressionResult.ratio
                )
            )
        } else {
            tempCompressedFile.delete()
            Result.failure(
                workDataOf(KEY_ERROR to (compressionResult.error ?: "Image compression failed"))
            )
        }
    }

    private fun createCompressionOptions(
        level: Int,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int
    ): AdvancedCompressionEngine.CompressionOptions {
        return when (level) {
            COMPRESSION_LEVEL_LOW -> AdvancedCompressionEngine.CompressionOptions(
                imageQuality = quality.coerceIn(90, 100),
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )
            COMPRESSION_LEVEL_MEDIUM -> AdvancedCompressionEngine.CompressionOptions(
                imageQuality = quality.coerceIn(75, 90),
                maxWidth = (maxWidth * 0.8f).toInt(),
                maxHeight = (maxHeight * 0.8f).toInt()
            )
            COMPRESSION_LEVEL_HIGH -> AdvancedCompressionEngine.CompressionOptions(
                imageQuality = quality.coerceIn(60, 80),
                maxWidth = (maxWidth * 0.6f).toInt(),
                maxHeight = (maxHeight * 0.6f).toInt()
            )
            COMPRESSION_LEVEL_MAXIMUM -> AdvancedCompressionEngine.CompressionOptions(
                imageQuality = quality.coerceIn(40, 70),
                maxWidth = (maxWidth * 0.4f).toInt(),
                maxHeight = (maxHeight * 0.4f).toInt()
            )
            else -> AdvancedCompressionEngine.CompressionOptions(
                imageQuality = quality,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )
        }
    }

    private fun extractImagesFromPdf(pdfFile: File): List<Bitmap> {
        // Simplified PDF image extraction
        // In a real implementation, you would use iText or similar library
        // to extract images from PDF pages
        
        val images = mutableListOf<Bitmap>()
        
        try {
            // For now, create a placeholder bitmap from the file name
            // This should be replaced with actual PDF image extraction
            val placeholder = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
            images.add(placeholder)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract images from PDF")
        }
        
        return images
    }
}