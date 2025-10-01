package com.jascanner.presentation.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jascanner.domain.model.EditablePage
import com.jascanner.domain.model.EdgePoint
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun ManualEdgeAdjustmentCanvas(
    page: EditablePage,
    onEdgesAdjusted: (List<EdgePoint>) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var edgePoints by remember {
        mutableStateOf(
            page.detectedEdges ?: generateInitialEdgePoints()
        )
    }
    var draggedPointIndex by remember { mutableStateOf<Int?>(null) }
    var showGrid by remember { mutableStateOf(true) }
    var snapToGrid by remember { mutableStateOf(true) }
    var magneticSnap by remember { mutableStateOf(true) }

    val bitmap = page.processedBitmap ?: page.originalBitmap ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        // Edge adjustment controls
        EdgeAdjustmentControlBar(
            showGrid = showGrid,
            snapToGrid = snapToGrid,
            magneticSnap = magneticSnap,
            onGridToggle = { showGrid = !showGrid },
            onSnapToggle = { snapToGrid = !snapToGrid },
            onMagneticToggle = { magneticSnap = !magneticSnap },
            onAutoDetect = {
                edgePoints = detectEdgesAutomatically(bitmap)
            },
            onReset = {
                edgePoints = generateInitialEdgePoints()
            },
            onApply = {
                onEdgesAdjusted(edgePoints)
            }
        )

        // Edge adjustment canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedPointIndex = findNearestEdgePoint(
                                offset,
                                edgePoints,
                                canvasSize
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggedPointIndex?.let { index ->
                                val newPoints = edgePoints.toMutableList()
                                val currentPoint = newPoints[index]

                                var newX = currentPoint.x + dragAmount.x / canvasSize.width
                                var newY = currentPoint.y + dragAmount.y / canvasSize.height

                                // Apply snap to grid
                                if (snapToGrid) {
                                    val gridSize = 0.05f
                                    newX = (newX / gridSize).toInt() * gridSize
                                    newY = (newY / gridSize).toInt() * gridSize
                                }

                                // Apply magnetic snap to edges
                                if (magneticSnap) {
                                    val snapThreshold = 0.02f
                                    if (abs(newX) < snapThreshold) newX = 0f
                                    if (abs(newX - 1f) < snapThreshold) newX = 1f
                                    if (abs(newY) < snapThreshold) newY = 0f
                                    if (abs(newY - 1f) < snapThreshold) newY = 1f
                                }

                                newPoints[index] = EdgePoint(
                                    x = newX.coerceIn(0f, 1f),
                                    y = newY.coerceIn(0f, 1f),
                                    type = currentPoint.type
                                )

                                edgePoints = newPoints
                            }
                        },
                        onDragEnd = {
                            draggedPointIndex = null
                        }
                    )
                }
        ) {
            val imageScale = minOf(
                size.width / bitmap.width,
                size.height / bitmap.height
            )
            val imageWidth = bitmap.width * imageScale
            val imageHeight = bitmap.height * imageScale
            val offsetX = (size.width - imageWidth) / 2
            val offsetY = (size.height - imageHeight) / 2

            // Draw image
            drawIntoCanvas { canvas ->
                canvas.drawImageRect(
                    image = bitmap.asImageBitmap(),
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(imageWidth.toInt(), imageHeight.toInt()),
                    paint = Paint()
                )
            }

            // Draw grid
            if (showGrid) {
                val gridLines = 20
                for (i in 1 until gridLines) {
                    val x = offsetX + (imageWidth / gridLines) * i
                    val y = offsetY + (imageHeight / gridLines) * i

                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(x, offsetY),
                        end = Offset(x, offsetY + imageHeight),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(offsetX, y),
                        end = Offset(offsetX + imageWidth, y),
                        strokeWidth = 1f
                    )
                }
            }

            // Draw edge path
            val path = Path().apply {
                edgePoints.forEachIndexed { index, point ->
                    val x = offsetX + point.x * imageWidth
                    val y = offsetY + point.y * imageHeight

                    if (index == 0) {
                        moveTo(x, y)
                    } else {
                        lineTo(x, y)
                    }
                }
                close()
            }

            drawPath(
                path = path,
                color = Color(0xFF2196F3),
                style = Stroke(width = 3f)
            )

            // Draw edge points with handles
            edgePoints.forEachIndexed { index, point ->
                val x = offsetX + point.x * imageWidth
                val y = offsetY + point.y * imageHeight
                val isDragged = draggedPointIndex == index
                val isCorner = point.type == EdgePoint.Type.CORNER

                // Draw handle
                drawCircle(
                    color = Color.White,
                    radius = if (isDragged) 30f else (if (isCorner) 24f else 18f),
                    center = Offset(x, y)
                )

                drawCircle(
                    color = when {
                        isDragged -> Color(0xFFFF9800)
                        isCorner -> Color(0xFF2196F3)
                        else -> Color(0xFF4CAF50)
                    },
                    radius = if (isDragged) 26f else (if (isCorner) 20f else 14f),
                    center = Offset(x, y)
                )

                // Draw point number for corners
                if (isCorner) {
                    val cornerIndex = edgePoints.filter { it.type == EdgePoint.Type.CORNER }
                        .indexOf(point) + 1
                    // Text would be drawn here with drawText API
                }
            }

            // Draw magnetic snap indicators
            if (magneticSnap && draggedPointIndex != null) {
                val snapLines = listOf(
                    Offset(offsetX, 0f) to Offset(offsetX, size.height),
                    Offset(offsetX + imageWidth, 0f) to Offset(offsetX + imageWidth, size.height),
                    Offset(0f, offsetY) to Offset(size.width, offsetY),
                    Offset(0f, offsetY + imageHeight) to Offset(size.width, offsetY + imageHeight)
                )

                snapLines.forEach { (start, end) ->
                    drawLine(
                        color = Color.Yellow.copy(alpha = 0.5f),
                        start = start,
                        end = end,
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeAdjustmentControlBar(
    showGrid: Boolean,
    snapToGrid: Boolean,
    magneticSnap: Boolean,
    onGridToggle: () -> Unit,
    onSnapToggle: () -> Unit,
    onMagneticToggle: () -> Unit,
    onAutoDetect: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Manual Edge Adjustment",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            // Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showGrid, onCheckedChange = { onGridToggle() })
                    Text("Show Grid", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = snapToGrid, onCheckedChange = { onSnapToggle() })
                    Text("Snap to Grid", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = magneticSnap, onCheckedChange = { onMagneticToggle() })
                    Text("Magnetic Snap", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAutoDetect,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto Detect")
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }

                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply")
                }
            }
        }
    }
}

private fun generateInitialEdgePoints(): List<EdgePoint> {
    return listOf(
        EdgePoint(0.05f, 0.05f, EdgePoint.Type.CORNER),
        EdgePoint(0.5f, 0.025f, EdgePoint.Type.EDGE),
        EdgePoint(0.95f, 0.05f, EdgePoint.Type.CORNER),
        EdgePoint(0.975f, 0.5f, EdgePoint.Type.EDGE),
        EdgePoint(0.95f, 0.95f, EdgePoint.Type.CORNER),
        EdgePoint(0.5f, 0.975f, EdgePoint.Type.EDGE),
        EdgePoint(0.05f, 0.95f, EdgePoint.Type.CORNER),
        EdgePoint(0.025f, 0.5f, EdgePoint.Type.EDGE)
    )
}

private fun findNearestEdgePoint(
    offset: Offset,
    edgePoints: List<EdgePoint>,
    canvasSize: IntSize
): Int? {
    val touchRadius = 50f

    edgePoints.forEachIndexed { index, point ->
        val x = point.x * canvasSize.width
        val y = point.y * canvasSize.height

        val distance = hypot(
            (offset.x - x).toDouble(),
            (offset.y - y).toDouble()
        )

        if (distance < touchRadius) {
            return index
        }
    }

    return null
}

private fun detectEdgesAutomatically(bitmap: android.graphics.Bitmap): List<EdgePoint> {
    // TODO: Implement a proper edge detection algorithm using OpenCV or ML Kit.
    // Simple edge detection algorithm
    try {
        // For now, return default points
        // In production, use OpenCV or ML Kit edge detection
        return generateInitialEdgePoints()
    } catch (e: Exception) {
        Timber.e(e, "Auto edge detection failed")
        return generateInitialEdgePoints()
    }
}