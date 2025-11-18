package com.aktarjabed.jascanner.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

object BitmapExtensions {

    fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
    }

    fun Bitmap.scaleToFit(maxDim: Int): Bitmap {
        val w = width
        val h = height
        val larger = maxOf(w, h)
        if (larger <= maxDim) return this
        val scale = maxDim.toFloat() / larger.toFloat()
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return Bitmap.createScaledBitmap(this, nw, nh, true)
    }

    fun Bitmap.saveToFile(outFile: File, quality: Int = 90): Boolean {
        return try {
            FileOutputStream(outFile).use { fos ->
                compress(Bitmap.CompressFormat.JPEG, quality, fos)
                fos.flush()
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Save failed", t)
            false
        }
    }

    fun Bitmap.cropToQuadrilateral(pts: Array<Point>): Bitmap? {
        if (pts.size != 4) return null
        try {
            val src = Mat()
            Utils.bitmapToMat(this, src)

            val srcPts = Mat(4, 1, CvType.CV_32FC2)
            val dstPts = Mat(4, 1, CvType.CV_32FC2)

            srcPts.put(0, 0, doubleArrayOf(pts[0].x, pts[0].y))
            srcPts.put(1, 0, doubleArrayOf(pts[1].x, pts[1].y))
            srcPts.put(2, 0, doubleArrayOf(pts[2].x, pts[2].y))
            srcPts.put(3, 0, doubleArrayOf(pts[3].x, pts[3].y))

            val wTop = distance(pts[0], pts[1])
            val wBottom = distance(pts[2], pts[3])
            val dstW = Math.max(wTop, wBottom).toInt()

            val hLeft = distance(pts[0], pts[3])
            val hRight = distance(pts[1], pts[2])
            val dstH = Math.max(hLeft, hRight).toInt()

            dstPts.put(0, 0, doubleArrayOf(0.0, 0.0))
            dstPts.put(1, 0, doubleArrayOf(dstW.toDouble(), 0.0))
            dstPts.put(2, 0, doubleArrayOf(dstW.toDouble(), dstH.toDouble()))
            dstPts.put(3, 0, doubleArrayOf(0.0, dstH.toDouble()))

            val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            val dstMat = Mat()
            Imgproc.warpPerspective(src, dstMat, M, Size(dstW.toDouble(), dstH.toDouble()))

            val outBmp = Bitmap.createBitmap(dstMat.cols(), dstMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, outBmp)

            src.release()
            dstMat.release()
            srcPts.release()
            dstPts.release()
            M.release()

            return outBmp
        } catch (t: Throwable) {
            Log.e(TAG, "cropToQuadrilateral failed", t)
            return null
        }
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.hypot(dx, dy)
    }

    private const val TAG = "BitmapExtensions"
}