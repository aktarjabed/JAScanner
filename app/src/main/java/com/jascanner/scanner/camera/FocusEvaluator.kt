package com.jascanner.scanner.camera

import android.graphics.Bitmap
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class FocusEvaluator @Inject constructor() {

    data class FocusResult(
        val sharpness: Double,
        val variance: Double,
        val isFocused: Boolean,
        val confidence: Double,
        val recommendation: String
    )

    fun evaluateFocus(bitmap: Bitmap): FocusResult {
        return try {
            val sharpness = calculateSharpness(bitmap)
            val variance = calculateVariance(bitmap)
            val isFocused = sharpness > SHARPNESS_THRESHOLD && variance > VARIANCE_THRESHOLD
            val confidence = calculateConfidence(sharpness, variance)
            val recommendation = generateRecommendation(sharpness, variance, isFocused)

            FocusResult(
                sharpness = sharpness,
                variance = variance,
                isFocused = isFocused,
                confidence = confidence,
                recommendation = recommendation
            )
        } catch (e: Exception) {
            Timber.e(e, "Focus evaluation failed")
            FocusResult(
                sharpness = 0.0,
                variance = 0.0,
                isFocused = false,
                confidence = 0.0,
                recommendation = "Focus evaluation failed"
            )
        }
    }

    private fun calculateSharpness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalGradient = 0.0
        var pixelCount = 0

        // Sobel edge detection
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // Get surrounding pixels
                val tl = getGrayValue(pixels[(y - 1) * width + (x - 1)])
                val tm = getGrayValue(pixels[(y - 1) * width + x])
                val tr = getGrayValue(pixels[(y - 1) * width + (x + 1)])
                val ml = getGrayValue(pixels[y * width + (x - 1)])
                val mr = getGrayValue(pixels[y * width + (x + 1)])
                val bl = getGrayValue(pixels[(y + 1) * width + (x - 1)])
                val bm = getGrayValue(pixels[(y + 1) * width + x])
                val br = getGrayValue(pixels[(y + 1) * width + (x + 1)])

                // Sobel operators
                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bm + br) - (tl + 2 * tm + tr)
                
                val gradient = sqrt((gx * gx + gy * gy).toDouble())
                totalGradient += gradient
                pixelCount++
            }
        }

        return if (pixelCount > 0) totalGradient / pixelCount else 0.0
    }

    private fun calculateVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate mean
        var sum = 0.0
        for (pixel in pixels) {
            sum += getGrayValue(pixel)
        }
        val mean = sum / pixels.size

        // Calculate variance
        var varianceSum = 0.0
        for (pixel in pixels) {
            val gray = getGrayValue(pixel)
            val diff = gray - mean
            varianceSum += diff * diff
        }

        return varianceSum / pixels.size
    }

    private fun getGrayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun calculateConfidence(sharpness: Double, variance: Double): Double {
        val sharpnessScore = (sharpness / MAX_SHARPNESS).coerceIn(0.0, 1.0)
        val varianceScore = (variance / MAX_VARIANCE).coerceIn(0.0, 1.0)
        
        return (sharpnessScore * 0.7 + varianceScore * 0.3) * 100
    }

    private fun generateRecommendation(
        sharpness: Double,
        variance: Double,
        isFocused: Boolean
    ): String {
        return when {
            isFocused -> "Image is well focused"
            sharpness < SHARPNESS_THRESHOLD * 0.5 -> "Image is very blurry - check focus"
            sharpness < SHARPNESS_THRESHOLD -> "Image is slightly blurry - refocus recommended"
            variance < VARIANCE_THRESHOLD -> "Low contrast - ensure good lighting"
            else -> "Focus quality unclear - try again"
        }
    }

    companion object {
        private const val SHARPNESS_THRESHOLD = 50.0
        private const val VARIANCE_THRESHOLD = 1000.0
        private const val MAX_SHARPNESS = 200.0
        private const val MAX_VARIANCE = 5000.0
    }
}