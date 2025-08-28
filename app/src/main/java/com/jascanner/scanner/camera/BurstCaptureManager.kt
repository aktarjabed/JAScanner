package com.jascanner.scanner.camera

import android.content.Context
import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import com.jascanner.scanner.camera.CameraController.CaptureResult
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BurstCaptureManager @Inject constructor() {

    data class BurstSettings(
        val count: Int = 3,
        val intervalMs: Long = 100L,
        val enableBracketing: Boolean = false,
        val bracketingSteps: List<Float> = listOf(-1.0f, 0.0f, 1.0f) // EV steps
    )

    suspend fun captureBurst(
        imageCapture: ImageCapture?,
        outputDir: File,
        count: Int = 3,
        context: Context,
        settings: BurstSettings = BurstSettings(count = count)
    ): List<CaptureResult> {
        if (imageCapture == null) {
            return listOf(CaptureResult(success = false, error = "ImageCapture not initialized"))
        }

        val results = mutableListOf<CaptureResult>()
        
        try {
            for (i in 0 until settings.count) {
                val outputFile = File(outputDir, "burst_${System.currentTimeMillis()}_$i.jpg")
                
                // Apply exposure compensation if bracketing is enabled
                if (settings.enableBracketing && i < settings.bracketingSteps.size) {
                    // Note: Exposure compensation would be applied via CameraControl
                    // This is a simplified version
                }

                val result = captureImage(imageCapture, outputFile, context)
                results.add(result)
                
                if (i < settings.count - 1) {
                    delay(settings.intervalMs)
                }
            }
            
            Timber.i("Burst capture completed: ${results.size} images")
        } catch (e: Exception) {
            Timber.e(e, "Burst capture failed")
            results.add(CaptureResult(success = false, error = e.message))
        }

        return results
    }

    private suspend fun captureImage(
        imageCapture: ImageCapture,
        outputFile: File,
        context: Context
    ): CaptureResult {
        return try {
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            
            var result: CaptureResult? = null
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        result = CaptureResult(
                            success = true,
                            bitmap = bitmap,
                            file = outputFile
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        result = CaptureResult(
                            success = false,
                            error = exception.message
                        )
                    }
                }
            )

            // Wait for capture to complete
            while (result == null) {
                delay(50)
            }
            
            result!!
        } catch (e: Exception) {
            CaptureResult(success = false, error = e.message)
        }
    }

    fun selectBestImage(results: List<CaptureResult>): CaptureResult? {
        return results
            .filter { it.success && it.bitmap != null }
            .maxByOrNull { result ->
                // Simple quality metric based on image size and focus
                result.bitmap?.let { bitmap ->
                    bitmap.width * bitmap.height * calculateSharpness(bitmap)
                } ?: 0.0
            }
    }

    private fun calculateSharpness(bitmap: android.graphics.Bitmap): Double {
        // Simplified sharpness calculation using edge detection
        // In a real implementation, you might use more sophisticated algorithms
        val width = bitmap.width
        val height = bitmap.height
        var sharpness = 0.0
        
        try {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Calculate gradient magnitude (simplified Sobel)
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    val gray = getGrayValue(pixels[idx])
                    val grayLeft = getGrayValue(pixels[idx - 1])
                    val grayRight = getGrayValue(pixels[idx + 1])
                    val grayUp = getGrayValue(pixels[(y - 1) * width + x])
                    val grayDown = getGrayValue(pixels[(y + 1) * width + x])
                    
                    val gx = grayRight - grayLeft
                    val gy = grayDown - grayUp
                    val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toDouble())
                    sharpness += magnitude
                }
            }
            
            sharpness /= ((width - 2) * (height - 2))
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate sharpness")
            sharpness = 0.0
        }
        
        return sharpness
    }

    private fun getGrayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}