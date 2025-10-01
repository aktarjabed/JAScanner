package com.jascanner.presentation.editor.tools

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.jascanner.utils.safeAsImageBitmap
import timber.log.Timber

fun DrawScope.drawImageLayer(
    bitmap: Bitmap,
    size: Size
) {
    try {
        if (bitmap.isRecycled) return

        val imageScale = minOf(
            size.width / bitmap.width,
            size.height / bitmap.height
        )
        val imageWidth = bitmap.width * imageScale
        val imageHeight = bitmap.height * imageScale
        val offsetX = (size.width - imageWidth) / 2
        val offsetY = (size.height - imageHeight) / 2

        bitmap.safeAsImageBitmap()?.let { imageBitmap ->
            drawIntoCanvas { canvas ->
                canvas.drawImageRect(
                    image = imageBitmap,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(imageWidth.toInt(), imageHeight.toInt()),
                paint = Paint()
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to draw image layer")
    }
}