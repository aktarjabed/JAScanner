package com.aktarjabed.jascanner.enhancer

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class ImageEnhancer {

    enum class EnhancementType {
        ORIGINAL,
        AUTO,
        BLACK_AND_WHITE,
        GRAYSCALE,
        MAGIC_COLOR
    }

    suspend fun enhanceAsync(bitmap: Bitmap, type: EnhancementType = EnhancementType.AUTO): Bitmap =
        withContext(Dispatchers.Default) { enhance(bitmap, type) }

    fun enhance(srcBitmap: Bitmap, type: EnhancementType = EnhancementType.AUTO): Bitmap {
        try {
            val src = Mat()
            Utils.bitmapToMat(srcBitmap, src)

            val result: Mat = when (type) {
                EnhancementType.ORIGINAL -> src.clone()
                EnhancementType.AUTO -> autoEnhance(src)
                EnhancementType.BLACK_AND_WHITE -> blackAndWhite(src)
                EnhancementType.GRAYSCALE -> grayscale(src)
                EnhancementType.MAGIC_COLOR -> magicColor(src)
            }

            val outBmp = Bitmap.createBitmap(result.cols(), result.rows(), Config.ARGB_8888)
            Utils.matToBitmap(result, outBmp)

            if (!src.isReleased) src.release()
            if (!result.isReleased) result.release()

            return outBmp
        } catch (t: Throwable) {
            Log.e(TAG, "Enhancement failed", t)
            return srcBitmap
        }
    }

    private fun autoEnhance(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val claheMat = Mat()
        clahe.apply(gray, claheMat)

        val out = Mat()
        Imgproc.cvtColor(claheMat, out, Imgproc.COLOR_GRAY2BGR)

        gray.release()
        claheMat.release()
        return out
    }

    private fun blackAndWhite(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 15, 10.0
        )

        val out = Mat()
        Imgproc.cvtColor(binary, out, Imgproc.COLOR_GRAY2BGR)

        gray.release()
        binary.release()
        return out
    }

    private fun grayscale(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val out = Mat()
        Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2BGR)
        gray.release()
        return out
    }

    private fun magicColor(src: Mat): Mat {
        val hsv = Mat()
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV)

        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)

        channels[1].convertTo(channels[1], CvType.CV_32F)
        Core.multiply(channels[1], Scalar(1.25), channels[1])
        channels[1].convertTo(channels[1], CvType.CV_8U)

        channels[2].convertTo(channels[2], CvType.CV_32F)
        Core.multiply(channels[2], Scalar(1.08), channels[2])
        channels[2].convertTo(channels[2], CvType.CV_8U)

        val merged = Mat()
        Core.merge(channels, merged)

        val out = Mat()
        Imgproc.cvtColor(merged, out, Imgproc.COLOR_HSV2BGR)

        channels.forEach { if (!it.isReleased) it.release() }
        hsv.release()
        merged.release()
        return out
    }

    companion object {
        private const val TAG = "ImageEnhancer"
    }
}