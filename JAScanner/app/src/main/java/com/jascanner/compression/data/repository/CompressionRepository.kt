package com.jascanner.compression.data.repository

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.jascanner.compression.domain.model.ColorMode
import com.jascanner.compression.domain.model.CompressionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class CompressionRepository {

    suspend fun compressBitmap(
        bitmap: Bitmap,
        settings: CompressionSettings
    ): Pair<Bitmap, List<String>> = withContext(Dispatchers.Default) {
        val appliedTechniques = mutableListOf<String>()
        var processedBitmap = bitmap

        // DPI-based scaling
        val dpi = settings.customDpi ?: settings.profile.maxDpi
        val scaleFactor = dpi.toFloat() / 300f // Assuming original is 300dpi for reference
        if (scaleFactor < 1.0f) {
            val newWidth = (processedBitmap.width * scaleFactor).toInt()
            val newHeight = (processedBitmap.height * scaleFactor).toInt()
            processedBitmap = processedBitmap.scale(newWidth, newHeight)
            appliedTechniques.add("Rescaled to $dpi DPI")
        }

        val mat = Mat()
        Utils.bitmapToMat(processedBitmap, mat)

        // Color Mode
        when (settings.colorMode) {
            ColorMode.GRAYSCALE -> {
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
                appliedTechniques.add("Converted to Grayscale")
            }
            ColorMode.MONOCHROME -> {
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
                Imgproc.threshold(mat, mat, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                appliedTechniques.add("Converted to Monochrome")
            }
            else -> {
                // AUTO or COLOR, do nothing here.
            }
        }

        // Denoising
        if (settings.enableDenoise) {
            Imgproc.medianBlur(mat, mat, 3)
            appliedTechniques.add("Denoised")
        }

        // Binarization
        if (settings.enableAdaptiveBinarization && settings.profile.applyBinarization) {
             if (settings.colorMode != ColorMode.MONOCHROME) { // Avoid applying twice
                val gray = Mat()
                if (mat.channels() > 1) {
                    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)
                } else {
                    mat.copyTo(gray)
                }
                Imgproc.adaptiveThreshold(
                    gray,
                    mat,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    11,
                    2.0
                )
                gray.release()
                appliedTechniques.add("Adaptive Binarization")
            }
        }

        val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, resultBitmap)
        mat.release()

        Pair(resultBitmap, appliedTechniques)
    }
}