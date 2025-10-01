package com.jascanner.edit.presentation.annotation

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import com.jascanner.presentation.editor.tools.detectDragGesturesSafe
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jascanner.utils.safeAsImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jascanner.edit.domain.model.Annotation
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    bitmap: Bitmap,
    pageId: String,
    existingAnnotations: List<Annotation>,
    onSave: (List<Annotation>) -> Unit,
    onCancel: () -> Unit
) {
    var annotations by remember { mutableStateOf(existingAnnotations) }
    var currentTool by remember { mutableStateOf(AnnotationTool.INK) }
    var currentPath by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var strokeColor by remember { mutableStateOf(android.graphics.Color.RED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annotate") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(annotations) }) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { currentTool = AnnotationTool.INK }) {
                        Icon(
                            Icons.Default.Edit,
                            "Ink",
                            tint = if (currentTool == AnnotationTool.INK) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { currentTool = AnnotationTool.HIGHLIGHT }) {
                        Icon(
                            Icons.Default.FormatColorFill,
                            "Highlight",
                            tint = if (currentTool == AnnotationTool.HIGHLIGHT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { currentTool = AnnotationTool.TEXT }) {
                        Icon(
                            Icons.Default.TextFields,
                            "Text",
                            tint = if (currentTool == AnnotationTool.TEXT) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        if (annotations.isNotEmpty()) {
                            annotations = annotations.dropLast(1)
                        }
                    }) {
                        Icon(Icons.Default.Undo, "Undo")
                    }
                }
            }
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
                    .pointerInput(currentTool) {
                        detectDragGesturesSafe(
                            onDragStart = { offset ->
                                currentPath = listOf(PointF(offset.x, offset.y))
                            },
                            onDrag = { change, _ ->
                                currentPath = currentPath + PointF(change.position.x, change.position.y)
                            },
                            onDragEnd = {
                                if (currentPath.isNotEmpty()) {
                                    val newAnnotation = when (currentTool) {
                                        AnnotationTool.INK -> Annotation.InkAnnotation(
                                            id = UUID.randomUUID().toString(),
                                            pageId = pageId,
                                            timestamp = System.currentTimeMillis(),
                                            points = currentPath,
                                            strokeWidth = 5f,
                                            color = strokeColor
                                        )
                                        else -> null
                                    }
                                    newAnnotation?.let {
                                        annotations = annotations + it
                                    }
                                    currentPath = emptyList()
                                }
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

                // Draw existing annotations
                annotations.forEach { annotation ->
                    when (annotation) {
                        is Annotation.InkAnnotation -> {
                            if (annotation.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(annotation.points.first().x, annotation.points.first().y)
                                    annotation.points.drop(1).forEach {
                                        lineTo(it.x, it.y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(annotation.color),
                                    style = Stroke(width = annotation.strokeWidth)
                                )
                            }
                        }
                        is Annotation.HighlightAnnotation -> {
                            drawRect(
                                color = Color(annotation.color).copy(alpha = annotation.opacity),
                                topLeft = Offset(annotation.boundingBox.left, annotation.boundingBox.top),
                                size = androidx.compose.ui.geometry.Size(
                                    annotation.boundingBox.width(),
                                    annotation.boundingBox.height()
                                )
                            )
                        }
                        else -> {}
                    }
                }

                // Draw current path
                if (currentPath.size > 1) {
                    val path = Path().apply {
                        moveTo(currentPath.first().x, currentPath.first().y)
                        currentPath.drop(1).forEach {
                            lineTo(it.x, it.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(strokeColor),
                        style = Stroke(width = 5f)
                    )
                }
            }
        }
    }
}

enum class AnnotationTool {
    INK, HIGHLIGHT, TEXT, REDACT, SIGNATURE
}