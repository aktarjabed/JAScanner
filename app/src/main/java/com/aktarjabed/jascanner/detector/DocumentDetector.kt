package com.aktarjabed.jascanner.detector

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DocumentDetector(
    private val minContourAreaRatio: Double = 0.1, // relative to image area
    private val approximationEpsilonFactor: Double = 0.02
) {

    fun detect(bitmap: Bitmap): List<Point>? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val width = mat.cols()
        val height = mat.rows()
        val imageArea = width.toDouble() * height.toDouble()

        // Preprocessing
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        // use adaptive threshold which is robust to lighting
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            gray, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 11, 2.0
        )

        // Morph close to reduce holes, then Canny for edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val closed = Mat()
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel)

        val edged = Mat()
        Imgproc.Canny(closed, edged, 50.0, 150.0)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestContour: MatOfPoint2f? = null
        var bestArea = 0.0

        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < imageArea * minContourAreaRatio) continue

            val contour2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, approximationEpsilonFactor * peri, true)

            if (approx.total() == 4L && area > bestArea && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                bestArea = area
                bestContour = approx
            }
        }

        if (bestContour == null) {
            // fallback: try less strict area threshold or return null
            return null
        }

        val pts = bestContour.toArray().toList()
        val sorted = sortCorners(pts)
        return sorted
    }

    /** Sort corner points to tl, tr, br, bl */
    private fun sortCorners(points: List<Point>): List<Point> {
        // Compute centroid
        val cx = points.map { it.x }.average()
        val cy = points.map { it.y }.average()

        val top = mutableListOf<Point>()
        val bottom = mutableListOf<Point>()
        for (p in points) {
            if (p.y < cy) top.add(p) else bottom.add(p)
        }

        val tl = top.minByOrNull { it.x }!!
        val tr = top.maxByOrNull { it.x }!!
        val bl = bottom.minByOrNull { it.x }!!
        val br = bottom.maxByOrNull { it.x }!!

        return listOf(tl, tr, br, bl)
    }
}