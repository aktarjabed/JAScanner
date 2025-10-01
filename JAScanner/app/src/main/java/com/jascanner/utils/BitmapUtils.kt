package com.jascanner.utils

import android.graphics.Bitmap

object BitmapUtils {

    fun recycleSafely(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to recycle bitmap")
        }
    }

    fun copyBitmapSafely(source: Bitmap?): Bitmap? {
        if (source == null || source.isRecycled) return null

        return try {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        } catch (e: Exception) {
            null
        }
    }

    fun scaleBitmapSafely(
        source: Bitmap?,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        if (source == null || source.isRecycled) return null

        return try {
            val ratio = minOf(
                maxWidth.toFloat() / source.width,
                maxHeight.toFloat() / source.height
            )

            if (ratio >= 1f) return source

            val newWidth = (source.width * ratio).toInt()
            val newHeight = (source.height * ratio).toInt()

            Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        }
    }
}