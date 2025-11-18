package com.aktarjabed.jascanner.util

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Utility helpers:
 * - imageProxyToBitmap converts YUV_420_888 ImageProxy to a correctly rotated Bitmap.
 * - createDownscaledBitmap reduces size preserving aspect ratio.
 */
object ImageUtils {

    // Convert ImageProxy (YUV_420_888) to Bitmap via NV21 -> YuvImage -> JPEG
    fun imageProxyToBitmap(image: ImageProxy, rotationDegrees: Int = 0): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        // U and V are swapped for NV21
        val chromaStride = image.planes[1].pixelStride
        val chromaRowStride = image.planes[1].rowStride

        // Fill NV21 interleaved VU
        var pos = ySize
        val width = image.width
        val height = image.height
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        // When pixelStride == 2 interleaved format, simpler copy; fallback generic loop for safety.
        if (uvPixelStride == 2) {
            // interleaved
            val u = ByteArray(uSize)
            val v = ByteArray(vSize)
            uBuffer.get(u)
            vBuffer.get(v)
            var ui = 0
            var vi = 0
            for (i in 0 until uSize step 1) {
                nv21[pos++] = v[vi++]
                nv21[pos++] = u[ui++]
            }
        } else {
            // Generic: iterate per row
            val rowStride = image.planes[1].rowStride
            val pixelStride = image.planes[1].pixelStride
            val vb = ByteArray(vSize)
            val ub = ByteArray(uSize)
            vBuffer.get(vb)
            uBuffer.get(ub)
            var index = 0
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vIndex = row * rowStride + col * pixelStride
                    val uIndex = row * rowStride + col * pixelStride
                    nv21[pos++] = vb.getOrElse(vIndex) { 0 }
                    nv21[pos++] = ub.getOrElse(uIndex) { 0 }
                }
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
            val bytes = out.toByteArray()
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
            bmp
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    fun createDownscaledBitmap(bitmap: Bitmap, maxLongEdge: Int = 640): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val long = maxOf(w, h)
        if (long <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / long.toFloat()
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}