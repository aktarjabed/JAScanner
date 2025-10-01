package com.jascanner.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

fun DrawScope.drawImageLayer(
    bitmap: Bitmap,
    canvasSize: Size
) {
    try {
        val imageScale = minOf(
            canvasSize.width / bitmap.width,
            canvasSize.height / bitmap.height
        )

        val imageWidth = (bitmap.width * imageScale).toInt()
        val imageHeight = (bitmap.height * imageScale).toInt()
        val offsetX = ((canvasSize.width - imageWidth) / 2).toInt()
        val offsetY = ((canvasSize.height - imageHeight) / 2).toInt()

        drawImage(
            image = bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(offsetX, offsetY),
            dstSize = IntSize(imageWidth, imageHeight)
        )
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to draw image layer")
    }
}