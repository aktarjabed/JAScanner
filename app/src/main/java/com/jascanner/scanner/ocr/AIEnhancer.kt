package com.jascanner.scanner.ocr

import android.content.Context
import android.graphics.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class AIEnhancer @Inject constructor(
    private val context: Context
) {
    
    data class EnhancementSettings(
        val contrastFactor: Float = 1.2f,
        val brightnessFactor: Float = 10f,
        val sharpenStrength: Float = 0.5f,
        val noiseReduction: Boolean = true,
        val deskew: Boolean = true,
        val binarize: Boolean = false
    )

    fun enhanceForOCR(
        bitmap: Bitmap,
        settings: EnhancementSettings = EnhancementSettings()
    ): Bitmap {
        return try {
            var enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Apply enhancements in order
            enhanced = adjustBrightnessContrast(enhanced, settings.brightnessFactor, settings.contrastFactor)
            
            if (settings.noiseReduction) {
                enhanced = applyGaussianBlur(enhanced, 1.0f)
            }
            
            if (settings.sharpenStrength > 0) {
                enhanced = applySharpen(enhanced, settings.sharpenStrength)
            }
            
            if (settings.deskew) {
                enhanced = deskewImage(enhanced)
            }
            
            if (settings.binarize) {
                enhanced = binarizeImage(enhanced)
            }
            
            enhanced
        } catch (e: Exception) {
            Timber.e(e, "Image enhancement failed")
            bitmap // Return original on failure
        }
    }

    private fun adjustBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }

    private fun applyGaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            isAntiAlias = true
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applySharpen(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val kernel = floatArrayOf(
            0f, -strength, 0f,
            -strength, 1 + 4 * strength, -strength,
            0f, -strength, 0f
        )
        
        val newPixels = applyConvolution(pixels, width, height, kernel, 3)
        result.setPixels(newPixels, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun applyConvolution(
        pixels: IntArray,
        width: Int,
        height: Int,
        kernel: FloatArray,
        kernelSize: Int
    ): IntArray {
        val result = IntArray(pixels.size)
        val offset = kernelSize / 2
        
        for (y in offset until height - offset) {
            for (x in offset until width - offset) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in -offset..offset) {
                    for (kx in -offset..offset) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        val kernelIndex = (ky + offset) * kernelSize + (kx + offset)
                        val pixel = pixels[pixelIndex]
                        val kernelValue = kernel[kernelIndex]
                        
                        r += ((pixel shr 16) and 0xFF) * kernelValue
                        g += ((pixel shr 8) and 0xFF) * kernelValue
                        b += (pixel and 0xFF) * kernelValue
                    }
                }
                
                val resultIndex = y * width + x
                result[resultIndex] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }
        
        return result
    }

    private fun deskewImage(bitmap: Bitmap): Bitmap {
        // Simplified deskewing using Hough transform approximation
        val angle = detectSkewAngle(bitmap)
        return if (kotlin.math.abs(angle) > 0.5) {
            rotateBitmap(bitmap, -angle)
        } else {
            bitmap
        }
    }

    private fun detectSkewAngle(bitmap: Bitmap): Float {
        // Simplified skew detection - in practice, you'd use more sophisticated algorithms
        val edges = detectEdges(bitmap)
        
        // Analyze horizontal lines to determine skew
        var angleSum = 0f
        var lineCount = 0
        
        val width = edges.width
        val height = edges.height
        
        for (y in height / 4 until 3 * height / 4 step 10) {
            val lineAngle = analyzeHorizontalLine(edges, y)
            if (kotlin.math.abs(lineAngle) < 15) { // Only consider reasonable angles
                angleSum += lineAngle
                lineCount++
            }
        }
        
        return if (lineCount > 0) angleSum / lineCount else 0f
    }

    private fun detectEdges(bitmap: Bitmap): Bitmap {
        val grayscale = convertToGrayscale(bitmap)
        
        // Apply Sobel edge detection
        val sobelX = floatArrayOf(
            -1f, 0f, 1f,
            -2f, 0f, 2f,
            -1f, 0f, 1f
        )
        
        val sobelY = floatArrayOf(
            -1f, -2f, -1f,
            0f, 0f, 0f,
            1f, 2f, 1f
        )
        
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val edgesX = applyConvolution(pixels, width, height, sobelX, 3)
        val edgesY = applyConvolution(pixels, width, height, sobelY, 3)
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val edgePixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val gx = (edgesX[i] and 0xFF).toFloat()
            val gy = (edgesY[i] and 0xFF).toFloat()
            val magnitude = kotlin.math.sqrt(gx * gx + gy * gy).toInt().coerceIn(0, 255)
            edgePixels[i] = Color.rgb(magnitude, magnitude, magnitude)
        }
        
        result.setPixels(edgePixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun analyzeHorizontalLine(bitmap: Bitmap, y: Int): Float {
        // Simplified line analysis - would use more sophisticated methods in practice
        return 0f // Placeholder
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f)
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun binarizeImage(bitmap: Bitmap): Bitmap {
        val grayscale = convertToGrayscale(bitmap)
        val width = grayscale.width
        val height = grayscale.height
        
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Calculate threshold using Otsu's method (simplified)
        val threshold = calculateOtsuThreshold(pixels)
        
        for (i in pixels.indices) {
            val gray = pixels[i] and 0xFF
            pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun calculateOtsuThreshold(pixels: IntArray): Int {
        // Simplified Otsu's threshold calculation
        val histogram = IntArray(256)
        
        // Build histogram
        for (pixel in pixels) {
            val gray = pixel and 0xFF
            histogram[gray]++
        }
        
        val total = pixels.size
        var sum = 0
        for (i in 0..255) {
            sum += i * histogram[i]
        }
        
        var sumB = 0
        var wB = 0
        var wF: Int
        var mB: Double
        var mF: Double
        var max = 0.0
        var between: Double
        var threshold1 = 0
        var threshold2 = 0
        
        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break
            sumB += i * histogram[i]
            mB = sumB.toDouble() / wB
            mF = (sum - sumB).toDouble() / wF
            between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between >= max) {
                threshold1 = i
                if (between > max) {
                    threshold2 = i
                }
                max = between
            }
        }
        
        return (threshold1 + threshold2) / 2
    }
}