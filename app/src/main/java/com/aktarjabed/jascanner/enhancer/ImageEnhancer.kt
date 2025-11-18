package com.aktarjabed.jascanner.enhancer

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ImageEnhancer {

    enum class EnhancementType {
        ORIGINAL,
        AUTO,          // Best overall enhancement
        BLACK_AND_WHITE,  // High contrast B&W
        GRAYSCALE,      // Simple grayscale
        MAGIC_COLOR      // Enhanced colors
    }

    /**
     * Apply enhancement to bitmap based on specified type
     */
    fun enhance(bitmap: Bitmap, type: EnhancementType = EnhancementType.AUTO): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val result = when (type) {
            EnhancementType.ORIGINAL -> mat.clone()
            EnhancementType.AUTO -> autoEnhance(mat)
            EnhancementType.BLACK_AND_WHITE -> blackAndWhite(mat)
            EnhancementType.GRAYSCALE -> grayscale(mat)
            EnhancementType.MAGIC_COLOR -> magicColor(mat)
        }

        val enhancedBitmap = Bitmap.createBitmap(
            result.cols(), result.rows(), Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(result, enhancedBitmap)

        return enhancedBitmap
    }

    /**
     * Automatic enhancement that determines the best approach based on image characteristics
     */
    private fun autoEnhance(mat: Mat): Mat {
        // Convert to grayscale for analysis
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
        val claheMat = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(gray, claheMat)

        return claheMat
    }

    /**
     * Convert to high contrast black and white
     */
    private fun blackAndWhite(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Apply adaptive threshold for better B&W conversion
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 15, 10.0
        )

        // Convert back to BGR for consistency
        val result = Mat()
        Imgproc.cvtColor(binary, result, Imgproc.COLOR_GRAY2BGR)

        return result
    }

    /**
     * Convert to grayscale
     */
    private fun grayscale(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

        // Convert back to BGR for consistency
        val result = Mat()
        Imgproc.cvtColor(gray, result, Imgproc.COLOR_GRAY2BGR)

        return result
    }

    /**
     * Enhance colors with saturation and contrast adjustment
     */
    private fun magicColor(mat: Mat): Mat {
        // Convert to HSV for better color manipulation
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)

        // Split channels
        val channels = mutableListOf<Mat>()
        Core.split(hsv, channels)

        // Increase saturation
        channels[1].convertTo(channels[1], CvType.CV_32F)
        Core.multiply(channels[1], Scalar(1.3), channels[1])
        channels[1].convertTo(channels[1], CvType.CV_8U)

        // Increase value (brightness) slightly
        channels[2].convertTo(channels[2], CvType.CV_32F)
        Core.multiply(channels[2], Scalar(1.1), channels[2])
        channels[2].convertTo(channels[2], CvType.CV_8U)

        // Merge channels back
        val enhanced = Mat()
        Core.merge(channels, enhanced)

        // Convert back to BGR
        val result = Mat()
        Imgproc.cvtColor(enhanced, result, Imgproc.COLOR_HSV2BGR)

        return result
    }
}