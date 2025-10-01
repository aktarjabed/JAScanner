package com.jascanner.presentation.editor.tools

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

fun drawImageLayer(scope: DrawScope, bitmap: Bitmap, size: Size) {
    with(scope) {
        val imageScale = minOf(
            size.width / bitmap.width,
            size.height / bitmap.height
        )
        val imageWidth = bitmap.width * imageScale
        val imageHeight = bitmap.height * imageScale
        val offsetX = (size.width - imageWidth) / 2
        val offsetY = (size.height - imageHeight) / 2

        drawIntoCanvas { canvas ->
            canvas.drawImageRect(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(imageWidth.toInt(), imageHeight.toInt()),
                paint = androidx.compose.ui.graphics.Paint()
            )
        }
    }
}

@Composable
fun ColorButton(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
    )
}