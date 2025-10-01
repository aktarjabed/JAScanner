package com.jascanner.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import timber.log.Timber

object BitmapUtils {

    /**
     * Safe bitmap copy that doesn't recycle the original
     */
    fun safeCopy(
        original: Bitmap,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        return try {
            if (original.isRecycled) {
                Timber.w("Attempted to copy recycled bitmap")
                return null
            }
            original.copy(config, true)
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory copying bitmap")
            System.gc()
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy bitmap")
            null
        }
    }

    /**
     * Safe bitmap processing with automatic cleanup
     */
    fun processWithCleanup(
        input: Bitmap,
        shouldRecycleInput: Boolean = false,
        processor: (Bitmap) -> Bitmap?
    ): Bitmap? {
        return try {
            if (input.isRecycled) {
                Timber.w("Input bitmap already recycled")
                return null
            }

            val result = processor(input)

            // Only recycle input if explicitly requested
            if (shouldRecycleInput && input != result) {
                input.recycle()
            }

            result
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory during processing")
            System.gc()
            null
        } catch (e: Exception) {
            Timber.e(e, "Bitmap processing failed")
            null
        }
    }

    /**
     * Check if bitmap is usable
     */
    fun isUsable(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
               !bitmap.isRecycled &&
               bitmap.width > 0 &&
               bitmap.height > 0
    }

    /**
     * Safe recycling with null check
     */
    fun safeRecycle(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to recycle bitmap")
        }
    }

    /**
     * Create scaled bitmap safely
     */
    fun createScaledBitmap(
        src: Bitmap,
        dstWidth: Int,
        dstHeight: Int,
        filter: Boolean = true
    ): Bitmap? {
        return try {
            if (src.isRecycled) return null

            if (dstWidth <= 0 || dstHeight <= 0) {
                Timber.w("Invalid dimensions for scaling: ${dstWidth}x${dstHeight}")
                return null
            }

            Bitmap.createScaledBitmap(src, dstWidth, dstHeight, filter)
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory scaling bitmap")
            System.gc()
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to scale bitmap")
            null
        }
    }
}