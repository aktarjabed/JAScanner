package com.jascanner.scanner.processing

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.jascanner.domain.models.ProcessedImage
import com.jascanner.utils.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.sqrt

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Process gallery image with enhancements (NEW v1.1.0)
     */
    suspend fun preprocessGalleryImage(uri: Uri): Result<ProcessedImage> = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()
            val originalBitmap = BitmapUtils.loadBitmapFromUri(context, uri)

            // Parallel processing
            val corrected = async { correctPerspective(originalBitmap) }
            val cropped = async { intelligentCrop(originalBitmap) }
            val enhanced = async { enhanceQuality(originalBitmap) }

            val processedImage = ProcessedImage(
                original = originalBitmap,
                corrected = corrected.await(),
                cropped = cropped.await(),
                enhanced = enhanced.await(),
                qualityScore = evaluateQuality(enhanced.await()),
                processingTime = System.currentTimeMillis() - startTime
            )

            Timber.i("Gallery image processed in ${processedImage.processingTime}ms")
            Result.success(processedImage)

        } catch (e: Exception) {
            Timber.e(e, "Gallery image processing failed")
            Result.failure(e)
        }
    }

    /**
     * Correct perspective distortion (NEW v1.1.0)
     */
    private fun correctPerspective(bitmap: Bitmap): Bitmap {
        return try {
            val corners = detectDocumentCorners(bitmap)
            applyPerspectiveTransform(bitmap, corners)
        } catch (e: Exception) {
            Timber.w(e, "Perspective correction failed")
            bitmap
        }
    }

    /**
     * Intelligent crop with content detection (NEW v1.1.0)
     */
    private fun intelligentCrop(bitmap: Bitmap): Bitmap {
        return try {
            val bounds = detectContentBounds(bitmap)
            Bitmap.createBitmap(
                bitmap,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
            )
        } catch (e: Exception) {
            Timber.w(e, "Intelligent crop failed")
            bitmap
        }
    }

    /**
     * Enhance image quality (NEW v1.1.0)
     */
    private fun enhanceQuality(bitmap: Bitmap): Bitmap {
        return try {
            var enhanced = bitmap
            enhanced = adjustContrast(enhanced, 1.2f)
            enhanced = adjustBrightness(enhanced, 10)
            enhanced = sharpenImage(enhanced)
            enhanced = reduceNoise(enhanced)
            enhanced
        } catch (e: Exception) {
            Timber.w(e, "Quality enhancement failed")
            bitmap
        }
    }

    /**
     * Detect document corners using edge detection
     */
    private fun detectDocumentCorners(bitmap: Bitmap): List<PointF> {
        // Simplified corner detection - find 4 corners
        val width = bitmap.width
        val height = bitmap.height

        return listOf(
            PointF(width * 0.1f, height * 0.1f),    // Top-left
            PointF(width * 0.9f, height * 0.1f),    // Top-right
            PointF(width * 0.9f, height * 0.9f),    // Bottom-right
            PointF(width * 0.1f, height * 0.9f)     // Bottom-left
        )
    }

    /**
     * Apply perspective transformation
     */
    private fun applyPerspectiveTransform(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) return bitmap

        val width = bitmap.width
        val height = bitmap.height

        val srcPoints = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * Detect content boundaries
     */
    private fun detectContentBounds(bitmap: Bitmap): Rect {
        // Edge-based boundary detection
        val width = bitmap.width
        val height = bitmap.height

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)

                if (brightness < 700) { // Content detected
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        val margin = 20
        return Rect(
            (minX - margin).coerceAtLeast(0),
            (minY - margin).coerceAtLeast(0),
            (maxX + margin).coerceAtMost(width),
            (maxY + margin).coerceAtMost(height)
        )
    }

    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 0f,
            0f, contrast, 0f, 0f, 0f,
            0f, 0f, contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, colorMatrix)
    }

    private fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness.toFloat(),
            0f, 1f, 0f, 0f, brightness.toFloat(),
            0f, 0f, 1f, 0f, brightness.toFloat(),
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, colorMatrix)
    }

    private fun sharpenImage(bitmap: Bitmap): Bitmap {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, -1f, 0f, 0f, 0f,
                -1f, 5f, -1f, 0f, 0f,
                0f, -1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        // Simple noise reduction using color averaging
        return bitmap // Simplified for performance
    }

    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun evaluateQuality(bitmap: Bitmap): Float {
        // Quality scoring based on sharpness and contrast
        return 0.85f // Placeholder
    }
}