package com.aktarjabed.jascanner.scanner

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class DocumentDetector {

    private val minContourAreaRatio = 0.1

    fun detect(bitmap: Bitmap): List<Point>? {
        var mat: Mat? = null
        var gray: Mat? = null
        var blurred: Mat? = null
        var edges: Mat? = null
        var dilated: Mat? = null

        try {
            mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            dilated = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, dilated, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = mat.rows() * mat.cols()
            val minArea = imageArea * minContourAreaRatio

            val largestContour = contours
                .filter { Imgproc.contourArea(it) > minArea }
                .maxByOrNull { Imgproc.contourArea(it) }

            if (largestContour != null) {
                val peri = Imgproc.arcLength(MatOfPoint2f(*largestContour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*largestContour.toArray()), approx, 0.02 * peri, true)

                if (approx.rows() == 4) {
                    return approx.toList()
                }
            }

            return null

        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            return null
        } finally {
            mat?.release()
            gray?.release()
            blurred?.release()
            edges?.release()
            dilated?.release()
        }
    }

    companion object {
        private const val TAG = "DocumentDetector"
    }
}