package com.jascanner.utils

import android.graphics.Bitmap
import timber.log.Timber

object BitmapUtils {
    fun safeRecycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
            Timber.d("Bitmap recycled safely.")
        }
    }
}