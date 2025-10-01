package com.jascanner.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

fun Bitmap.toImageBitmap(): ImageBitmap = this.asImageBitmap()

fun Bitmap.safeAsImageBitmap(): ImageBitmap? {
    return try {
        if (!this.isRecycled) {
            this.asImageBitmap()
        } else {
            null
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to convert bitmap to ImageBitmap")
        null
    }
}