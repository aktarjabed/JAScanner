package com.jascanner.compression.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.jascanner.compression.domain.model.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MultiFormatCompressor(
    private val context: Context,
    private val baseCompressor: CompressionRepository
) {

    suspend fun compressAndExport(
        bitmaps: List<Bitmap>,
        settings: CompressionSettings,
        outputFile: File,
        formatSettings: FormatSpecificSettings = FormatSpecificSettings()
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val originalSize = estimateOriginalSize(bitmaps)

        try {
            when (settings.outputFormat) {
                OutputFormat.JPG -> exportAsJpeg(bitmaps, settings, formatSettings, outputFile)
                OutputFormat.PNG -> exportAsPng(bitmaps, settings, formatSettings, outputFile)
                OutputFormat.WEBP -> exportAsWebP(bitmaps, settings, formatSettings, outputFile)
                OutputFormat.PDF -> exportAsPdf(bitmaps, settings, formatSettings, outputFile)
            }

            val endTime = System.currentTimeMillis()
            val compressedSize = outputFile.length()

            Result.success(
                CompressionResult(
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = compressedSize,
                    compressionRatio = ((1 - compressedSize.toFloat() / originalSize) * 100),
                    processingTimeMs = endTime - startTime,
                    appliedTechniques = listOf("${settings.outputFormat.displayName} Export"),
                    outputFormat = settings.outputFormat,
                    pageCount = bitmaps.size,
                    averageSizePerPage = compressedSize / bitmaps.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exportAsJpeg(
        bitmaps: List<Bitmap>,
        settings: CompressionSettings,
        formatSettings: FormatSpecificSettings,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        // For multi-page, create ZIP or save first page only
        val bitmap = if (bitmaps.size == 1) {
            bitmaps.first()
        } else {
            // Save first page or merge vertically
            bitmaps.first()
        }

        val (compressed, _) = baseCompressor.compressBitmap(bitmap, settings)

        FileOutputStream(outputFile).use { fos ->
            val quality = settings.customQuality ?: settings.profile.jpegQuality

            if (formatSettings.jpegProgressive) {
                // Android doesn't support progressive JPEG natively
                // Use standard compression
                compressed.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            } else {
                compressed.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
        }
    }

    private suspend fun exportAsPng(
        bitmaps: List<Bitmap>,
        settings: CompressionSettings,
        formatSettings: FormatSpecificSettings,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val bitmap = bitmaps.first()
        val (compressed, _) = baseCompressor.compressBitmap(bitmap, settings)

        // Convert to appropriate bit depth
        val finalBitmap = when (formatSettings.pngBitDepth) {
            PngBitDepth.DEPTH_1 -> convertToMonochrome(compressed)
            PngBitDepth.DEPTH_8 -> convertToGrayscale(compressed)
            else -> compressed
        }

        FileOutputStream(outputFile).use { fos ->
            // The quality parameter is ignored for PNG.
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    private suspend fun exportAsWebP(
        bitmaps: List<Bitmap>,
        settings: CompressionSettings,
        formatSettings: FormatSpecificSettings,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val bitmap = bitmaps.first()
        val (compressed, _) = baseCompressor.compressBitmap(bitmap, settings)

        FileOutputStream(outputFile).use { fos ->
            val quality = settings.customQuality ?: settings.profile.webpQuality

            // Android 9.0+ supports WebP with quality control
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (formatSettings.webpLossless) {
                    compressed.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos)
                } else {
                    compressed.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
                }
            } else {
                // Fallback for older Android versions
                compressed.compress(Bitmap.CompressFormat.WEBP, quality, fos)
            }
        }
    }

    private suspend fun exportAsPdf(
        bitmaps: List<Bitmap>,
        settings: CompressionSettings,
        formatSettings: FormatSpecificSettings,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val document = PDDocument()

        bitmaps.forEach { bitmap ->
            val (compressed, _) = baseCompressor.compressBitmap(bitmap, settings)

            val dpi = settings.customDpi?.toFloat() ?: settings.profile.maxDpi.toFloat()
            val widthPts = compressed.width * 72f / dpi
            val heightPts = compressed.height * 72f / dpi

            val page = PDPage(PDRectangle(widthPts, heightPts))
            document.addPage(page)

            val contentStream = PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                formatSettings.pdfCompressStreams,
                true
            )

            // Choose compression method based on profile
            val pdImage = when {
                settings.profile == CompressionProfile.MAXIMUM_QUALITY -> {
                    LosslessFactory.createFromImage(document, compressed)
                }
                settings.profile == CompressionProfile.HIGH_QUALITY -> {
                    JPEGFactory.createFromImage(document, compressed, 0.95f)
                }
                else -> {
                    val quality = settings.profile.jpegQuality / 100f
                    JPEGFactory.createFromImage(document, compressed, quality)
                }
            }

            contentStream.drawImage(pdImage, 0f, 0f, widthPts, heightPts)
            contentStream.close()
        }

        // Apply PDF-specific optimizations
        if (formatSettings.pdfRemoveMetadata) {
            document.documentInformation.clear()
        }

        document.save(outputFile)
        document.close()
    }

    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        val binaryMat = Mat()
        Imgproc.threshold(
            grayMat,
            binaryMat,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
        )

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(binaryMat, result)

        mat.release()
        grayMat.release()
        binaryMat.release()

        return result
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayMat, result)

        mat.release()
        grayMat.release()

        return result
    }

    private fun estimateOriginalSize(bitmaps: List<Bitmap>): Long {
        return bitmaps.sumOf { bitmap ->
            (bitmap.width * bitmap.height * 4).toLong()
        }
    }
}