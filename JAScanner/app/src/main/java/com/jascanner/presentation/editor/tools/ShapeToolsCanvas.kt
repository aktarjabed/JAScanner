package com.jascanner.presentation.editor.tools

import android.graphics.PointF
import android.graphics.RectF
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jascanner.domain.model.*
import com.jascanner.presentation.editor.tools.EditorUtils.drawImageLayer
import com.jascanner.presentation.editor.tools.EditorUtils.ColorButton
import timber.log.Timber
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ShapeToolsCanvas(
    page: EditablePage,
    onShapeAdded: (Annotation.ShapeAnnotation) -> Unit
) {
    var selectedShape by remember { mutableStateOf(ShapeType.RECTANGLE) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var strokeColor by remember { mutableStateOf(android.graphics.Color.RED) }
    var strokeWidth by remember { mutableStateOf(3f) }
    var fillColor by remember { mutableStateOf<Int?>(null) }
    var arrowHeadSize by remember { mutableStateOf(20f) }

    val bitmap = page.processedBitmap ?: page.originalBitmap ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        // Shape tools control bar
        ShapeToolsControlBar(
            selectedShape = selectedShape,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            arrowHeadSize = arrowHeadSize,
            onShapeSelected = { selectedShape = it },
            onStrokeColorChange = { strokeColor = it },
            onStrokeWidthChange = { strokeWidth = it },
            onFillColorChange = { fillColor = it },
            onArrowHeadSizeChange = { arrowHeadSize = it }
        )

        // Drawing canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .pointerInput(selectedShape) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startPoint = offset
                            endPoint = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            endPoint = change.position
                        },
                        onDragEnd = {
                            if (startPoint != null && endPoint != null) {
                                try {
                                    val shape = createShapeAnnotation(
                                        page.pageId,
                                        selectedShape,
                                        startPoint!!,
                                        endPoint!!,
                                        strokeColor,
                                        strokeWidth,
                                        fillColor,
                                        arrowHeadSize
                                    )
                                    onShapeAdded(shape)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to create shape")
                                }
                            }
                            startPoint = null
                            endPoint = null
                        }
                    )
                }
        ) {
            // Draw image
            drawImageLayer(this, bitmap, size)

            // Draw existing shapes
            page.annotations.forEach { annotation ->
                when (annotation) {
                    is Annotation.ShapeAnnotation -> drawShape(this, annotation)
                    else -> {}
                }
            }

            // Draw current shape preview
            if (startPoint != null && endPoint != null) {
                drawShapePreview(
                    this,
                    selectedShape,
                    startPoint!!,
                    endPoint!!,
                    strokeColor,
                    strokeWidth,
                    fillColor,
                    arrowHeadSize
                )
            }
        }
    }
}

@Composable
private fun ShapeToolsControlBar(
    selectedShape: ShapeType,
    strokeColor: Int,
    strokeWidth: Float,
    fillColor: Int?,
    arrowHeadSize: Float,
    onShapeSelected: (ShapeType) -> Unit,
    onStrokeColorChange: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onFillColorChange: (Int?) -> Unit,
    onArrowHeadSizeChange: (Float) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Shape Tools", style = MaterialTheme.typography.titleSmall)

            Spacer(Modifier.height(8.dp))

            // Shape selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeType.values().forEach { shape ->
                    FilterChip(
                        selected = selectedShape == shape,
                        onClick = { onShapeSelected(shape) },
                        label = { Text(shape.displayName) },
                        leadingIcon = {
                            Icon(shape.icon, null, Modifier.size(16.dp))
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stroke color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Stroke:", modifier = Modifier.width(60.dp))
                listOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.BLUE,
                    android.graphics.Color.GREEN,
                    android.graphics.Color.BLACK,
                    android.graphics.Color.YELLOW
                ).forEach { color ->
                    ColorButton(
                        color = color,
                        isSelected = strokeColor == color,
                        onClick = { onStrokeColorChange(color) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Fill color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fill:", modifier = Modifier.width(60.dp))

                FilterChip(
                    selected = fillColor == null,
                    onClick = { onFillColorChange(null) },
                    label = { Text("None") }
                )

                listOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.BLUE,
                    android.graphics.Color.GREEN,
                    android.graphics.Color.YELLOW
                ).forEach { color ->
                    val alphaColor = android.graphics.Color.argb(80,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color)
                    )
                    ColorButton(
                        color = alphaColor,
                        isSelected = fillColor == alphaColor,
                        onClick = { onFillColorChange(alphaColor) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stroke width
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Width:", modifier = Modifier.width(60.dp))
                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 1f..10f,
                    modifier = Modifier.weight(1f)
                )
                Text("${strokeWidth.toInt()}px", modifier = Modifier.width(50.dp))
            }

            // Arrow head size (for arrows only)
            if (selectedShape == ShapeType.ARROW) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arrow Size:", modifier = Modifier.width(90.dp))
                    Slider(
                        value = arrowHeadSize,
                        onValueChange = onArrowHeadSizeChange,
                        valueRange = 10f..50f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${arrowHeadSize.toInt()}", modifier = Modifier.width(50.dp))
                }
            }
        }
    }
}

private fun createShapeAnnotation(
    pageId: String,
    shapeType: ShapeType,
    start: Offset,
    end: Offset,
    strokeColor: Int,
    strokeWidth: Float,
    fillColor: Int?,
    arrowHeadSize: Float
): Annotation.ShapeAnnotation {
    val boundingBox = RectF(
        kotlin.math.min(start.x, end.x),
        kotlin.math.min(start.y, end.y),
        kotlin.math.max(start.x, end.x),
        kotlin.math.max(start.y, end.y)
    )

    return Annotation.ShapeAnnotation(
        id = UUID.randomUUID().toString(),
        pageId = pageId,
        timestamp = System.currentTimeMillis(),
        layer = 0,
        shapeType = shapeType,
        boundingBox = boundingBox,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        fillColor = fillColor,
        startPoint = PointF(start.x, start.y),
        endPoint = PointF(end.x, end.y),
        arrowHeadSize = arrowHeadSize
    )
}

private fun drawShapePreview(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    shapeType: ShapeType,
    start: Offset,
    end: Offset,
    strokeColor: Int,
    strokeWidth: Float,
    fillColor: Int?,
    arrowHeadSize: Float
) {
    with(scope) {
        when (shapeType) {
            ShapeType.RECTANGLE -> {
                val topLeft = Offset(
                    kotlin.math.min(start.x, end.x),
                    kotlin.math.min(start.y, end.y)
                )
                val size = Size(
                    kotlin.math.abs(end.x - start.x),
                    kotlin.math.abs(end.y - start.y)
                )

                // Draw fill
                fillColor?.let {
                    drawRect(
                        color = Color(it),
                        topLeft = topLeft,
                        size = size
                    )
                }

                // Draw stroke
                drawRect(
                    color = Color(strokeColor),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = strokeWidth)
                )
            }

            ShapeType.CIRCLE -> {
                val center = Offset(
                    (start.x + end.x) / 2,
                    (start.y + end.y) / 2
                )
                val radius = kotlin.math.hypot(
                    (end.x - start.x).toDouble(),
                    (end.y - start.y).toDouble()
                ).toFloat() / 2

                // Draw fill
                fillColor?.let {
                    drawCircle(
                        color = Color(it),
                        radius = radius,
                        center = center
                    )
                }

                // Draw stroke
                drawCircle(
                    color = Color(strokeColor),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }

            ShapeType.ARROW -> {
                drawArrow(
                    start = start,
                    end = end,
                    color = Color(strokeColor),
                    strokeWidth = strokeWidth,
                    arrowHeadSize = arrowHeadSize
                )
            }

            ShapeType.LINE -> {
                drawLine(
                    color = Color(strokeColor),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ShapeType.ELLIPSE -> {
                val topLeft = Offset(
                    kotlin.math.min(start.x, end.x),
                    kotlin.math.min(start.y, end.y)
                )
                val size = Size(
                    kotlin.math.abs(end.x - start.x),
                    kotlin.math.abs(end.y - start.y)
                )

                // Draw fill
                fillColor?.let {
                    drawOval(
                        color = Color(it),
                        topLeft = topLeft,
                        size = size
                    )
                }

                // Draw stroke
                drawOval(
                    color = Color(strokeColor),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
    arrowHeadSize: Float
) {
    // Draw line
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Calculate arrow head
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowAngle = Math.toRadians(30.0)

    val arrowPoint1 = Offset(
        (end.x - arrowHeadSize * cos(angle - arrowAngle)).toFloat(),
        (end.y - arrowHeadSize * sin(angle - arrowAngle)).toFloat()
    )

    val arrowPoint2 = Offset(
        (end.x - arrowHeadSize * cos(angle + arrowAngle)).toFloat(),
        (end.y - arrowHeadSize * sin(angle + arrowAngle)).toFloat()
    )

    // Draw arrow head
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        moveTo(end.x, end.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun drawShape(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    annotation: Annotation.ShapeAnnotation
) {
    with(scope) {
        val start = Offset(annotation.startPoint.x, annotation.startPoint.y)
        val end = Offset(annotation.endPoint.x, annotation.endPoint.y)

        drawShapePreview(
            this,
            annotation.shapeType,
            start,
            end,
            annotation.strokeColor,
            annotation.strokeWidth,
            annotation.fillColor,
            annotation.arrowHeadSize
        )
    }
}