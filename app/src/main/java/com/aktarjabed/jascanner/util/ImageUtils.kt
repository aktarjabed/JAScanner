package com.aktarjabed.jascanner.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object ImageUtils {
    /**
     * Convert ImageProxy (YUV_420_888) -> Bitmap using NV21 -> YuvImage -> JPEG -> Bitmap path.
     * This is robust across devices and avoids fragile per-plane pixelStride assumptions.
     *
     * rotationDegrees: you may pass imageProxy.imageInfo.rotationDegrees (0/90/180/270)
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int = 0): Bitmap? {
        val image = imageProxy.image ?: return null
        val width = image.width
        val height = image.height

        // Convert YUV_420_888 -> NV21
        val nv21 = yuv420888ToNv21(image)

        // Compress NV21 to JPEG
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        val rect = Rect(0, 0, width, height)
        // Quality 90 is a good tradeoff
        if (!yuvImage.compressToJpeg(rect, 90, out)) {
            out.close()
            return null
        }
        val jpegBytes = out.toByteArray()
        out.close()

        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

        // Rotate if necessary
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            bitmap = rotated
        }

        return bitmap
    }

    /**
     * Create a downscaled bitmap for analysis to reduce CPU and memory usage.
     * Keeps aspect ratio. Uses nearest scaling through Bitmap.createScaledBitmap (fast).
     */
    fun createDownscaledBitmap(bitmap: Bitmap, maxDim: Int = 640): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val larger = maxOf(width, height)
        if (larger <= maxDim) return bitmap // no-op; caller must handle recycling appropriately

        val scale = maxDim.toFloat() / larger.toFloat()
        val dstW = (width * scale).toInt()
        val dstH = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
    }

    /**
     * Convert ImageProxy.image (YUV_420_888) to NV21 byte array.
     * Reliable across devices. Faster path than multiple allocations could be added later.
     */
    private fun yuv420888ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Copy Y
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        var pos = 0
        if (pixelStride == 1 && rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            val yRow = ByteArray(rowStride)
            for (r in 0 until height) {
                yBuffer.get(yRow, 0, rowStride)
                System.arraycopy(yRow, 0, nv21, pos, width)
                pos += width
            }
        }

        // U and V are swapped and may have different strides; we need NV21 = VU VU ...
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Iterate over chroma rows
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uRow = ByteArray(uRowStride)
        val vRow = ByteArray(vRowStride)

        var uvPos = ySize
        for (r in 0 until chromaHeight) {
            // read V and U rows
            vBuffer.get(vRow, 0, vRowStride)
            uBuffer.get(uRow, 0, uRowStride)
            var ui = 0
            var vi = 0
            for (c in 0 until chromaWidth) {
                val v = vRow[vi]
                val u = uRow[ui]
                nv21[uvPos++] = v
                nv21[uvPos++] = u
                ui += uPixelStride
                vi += vPixelStride
            }
        }
        return nv21
    }
}