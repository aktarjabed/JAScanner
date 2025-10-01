package com.jascanner.edit.presentation.crop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import com.jascanner.presentation.editor.tools.detectDragGesturesSafe
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jascanner.utils.safeAsImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onApply: (RectF) -> Unit,
    onCancel: () -> Unit
) {
    var cropRect by remember {
        mutableStateOf(
            RectF(
                bitmap.width * 0.1f,
                bitmap.height * 0.1f,
                bitmap.width * 0.9f,
                bitmap.height * 0.9f
            )
        )
    }

    var dragHandle by remember { mutableStateOf<CropHandle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Image") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { onApply(cropRect) }) {
                        Icon(Icons.Default.Check, "Apply")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGesturesSafe(
                            onDragStart = { offset ->
                                dragHandle = detectHandle(offset, cropRect, size.width.toFloat(), size.height.toFloat())
                            },
                            onDrag = { change, dragAmount ->
                                dragHandle?.let { handle ->
                                    cropRect = updateCropRect(cropRect, handle, dragAmount, bitmap.width.toFloat(), bitmap.height.toFloat())
                                }
                            },
                            onDragEnd = {
                                dragHandle = null
                            }
                        )
                    }
            ) {
                // Draw bitmap
                bitmap.safeAsImageBitmap()?.let {
                    drawImage(
                        image = it,
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                }

                // Draw overlay
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    size = size
                )

                // Draw crop window (clear)
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(cropRect.left, cropRect.top),
                    size = Size(cropRect.width(), cropRect.height()),
                    blendMode = BlendMode.Clear
                )

                // Draw crop border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropRect.left, cropRect.top),
                    size = Size(cropRect.width(), cropRect.height()),
                    style = Stroke(width = 4f)
                )

                // Draw corner handles
                val handleRadius = 20f
                listOf(
                    Offset(cropRect.left, cropRect.top),
                    Offset(cropRect.right, cropRect.top),
                    Offset(cropRect.left, cropRect.bottom),
                    Offset(cropRect.right, cropRect.bottom)
                ).forEach { corner ->
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius,
                        center = corner
                    )
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = handleRadius - 4f,
                        center = corner
                    )
                }
            }
        }
    }
}

enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

private fun detectHandle(
    offset: Offset,
    cropRect: RectF,
    canvasWidth: Float,
    canvasHeight: Float
): CropHandle? {
    val touchRadius = 60f

    return when {
        abs(offset.x - cropRect.left) < touchRadius && abs(offset.y - cropRect.top) < touchRadius -> CropHandle.TOP_LEFT
        abs(offset.x - cropRect.right) < touchRadius && abs(offset.y - cropRect.top) < touchRadius -> CropHandle.TOP_RIGHT
        abs(offset.x - cropRect.left) < touchRadius && abs(offset.y - cropRect.bottom) < touchRadius -> CropHandle.BOTTOM_LEFT
        abs(offset.x - cropRect.right) < touchRadius && abs(offset.y - cropRect.bottom) < touchRadius -> CropHandle.BOTTOM_RIGHT
        offset.x > cropRect.left && offset.x < cropRect.right && offset.y > cropRect.top && offset.y < cropRect.bottom -> CropHandle.CENTER
        else -> null
    }
}

private fun updateCropRect(
    current: RectF,
    handle: CropHandle,
    dragAmount: Offset,
    maxWidth: Float,
    maxHeight: Float
): RectF {
    return when (handle) {
        CropHandle.TOP_LEFT -> RectF(
            (current.left + dragAmount.x).coerceIn(0f, current.right - 50f),
            (current.top + dragAmount.y).coerceIn(0f, current.bottom - 50f),
            current.right,
            current.bottom
        )
        CropHandle.TOP_RIGHT -> RectF(
            current.left,
            (current.top + dragAmount.y).coerceIn(0f, current.bottom - 50f),
            (current.right + dragAmount.x).coerceIn(current.left + 50f, maxWidth),
            current.bottom
        )
        CropHandle.BOTTOM_LEFT -> RectF(
            (current.left + dragAmount.x).coerceIn(0f, current.right - 50f),
            current.top,
            current.right,
            (current.bottom + dragAmount.y).coerceIn(current.top + 50f, maxHeight)
        )
        CropHandle.BOTTOM_RIGHT -> RectF(
            current.left,
            current.top,
            (current.right + dragAmount.x).coerceIn(current.left + 50f, maxWidth),
            (current.bottom + dragAmount.y).coerceIn(current.top + 50f, maxHeight)
        )
        CropHandle.CENTER -> RectF(
            (current.left + dragAmount.x).coerceIn(0f, maxWidth - current.width()),
            (current.top + dragAmount.y).coerceIn(0f, maxHeight - current.height()),
            (current.right + dragAmount.x).coerceIn(current.width(), maxWidth),
            (current.bottom + dragAmount.y).coerceIn(current.height(), maxHeight)
        )
    }
}