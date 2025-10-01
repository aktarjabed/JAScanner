package com.jascanner.presentation.editor.tools

import android.graphics.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jascanner.domain.model.EditablePage
import com.jascanner.presentation.editor.tools.EditorUtils.drawImageLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.sqrt

@Composable
fun BackgroundRemovalCanvas(
    page: EditablePage,
    onBackgroundRemoved: (Bitmap) -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tolerance by remember { mutableStateOf(30) }
    var selectedPoints by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var mode by remember { mutableStateOf(BackgroundRemovalMode.AUTO) }

    val scope = rememberCoroutineScope()
    val originalBitmap = page.processedBitmap ?: page.originalBitmap ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        // Control bar
        BackgroundRemovalControlBar(
            mode = mode,
            tolerance = tolerance,
            onModeChange = { mode = it },
            onToleranceChange = { tolerance = it },
            onProcess = {
                scope.launch {
                    isProcessing = true
                    try {
                        val result = withContext(Dispatchers.Default) {
                            when (mode) {
                                BackgroundRemovalMode.AUTO -> removeBackgroundAuto(originalBitmap)
                                BackgroundRemovalMode.MANUAL -> {
                                    if (selectedPoints.isNotEmpty()) {
                                        removeBackgroundManual(originalBitmap, selectedPoints, tolerance)
                                    } else {
                                        originalBitmap
                                    }
                                }
                                BackgroundRemovalMode.SHADOW -> removeShadows(originalBitmap)
                            }
                        }
                        previewBitmap = result
                    } catch (e: Exception) {
                        Timber.e(e, "Background removal failed")
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onApply = {
                previewBitmap?.let { onBackgroundRemoved(it) }
            },
            onReset = {
                previewBitmap = null
                selectedPoints = emptyList()
            }
        )

        // Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode) {
                        if (mode == BackgroundRemovalMode.MANUAL) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    selectedPoints = listOf(
                                        PointF(offset.x, offset.y)
                                    )
                                },
                                onDrag = { _, _ -> },
                                onDragEnd = {}
                            )
                        }
                    }
            ) {
                val displayBitmap = previewBitmap ?: originalBitmap
                drawImageLayer(this, displayBitmap, size)

                // Draw selection points
                if (mode == BackgroundRemovalMode.MANUAL) {
                    selectedPoints.forEach { point ->
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.Red,
                            radius = 20f,
                            center = androidx.compose.ui.geometry.Offset(point.x, point.y)
                        )
                    }
                }
            }

            if (isProcessing) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun BackgroundRemovalControlBar(
    mode: BackgroundRemovalMode,
    tolerance: Int,
    onModeChange: (BackgroundRemovalMode) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onProcess: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Background Removal",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            // Mode selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackgroundRemovalMode.values().forEach { removalMode ->
                    FilterChip(
                        selected = mode == removalMode,
                        onClick = { onModeChange(removalMode) },
                        label = { Text(removalMode.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tolerance slider (for manual mode)
            if (mode == BackgroundRemovalMode.MANUAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tolerance:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = tolerance.toFloat(),
                        onValueChange = { onToleranceChange(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$tolerance", modifier = Modifier.width(50.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }

                Button(
                    onClick = onProcess,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Process")
                }

                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply")
                }
            }
        }
    }
}

enum class BackgroundRemovalMode(val displayName: String) {
    AUTO("Auto"),
    MANUAL("Manual"),
    SHADOW("Shadow Removal")
}

// Background removal algorithms

private fun removeBackgroundAuto(bitmap: Bitmap): Bitmap {
    try {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        // Detect background color from corners
        val corners = listOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(bitmap.width - 1, 0),
            bitmap.getPixel(0, bitmap.height - 1),
            bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        )

        val bgColor = corners.groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: Color.WHITE

        val threshold = 50

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                try {
                    val pixel = bitmap.getPixel(x, y)

                    if (colorDistance(pixel, bgColor) < threshold) {
                        result.setPixel(x, y, Color.TRANSPARENT)
                    } else {
                        result.setPixel(x, y, pixel)
                    }
                } catch (e: Exception) {
                    // Skip problematic pixels
                }
            }
        }

        return result
    } catch (e: Exception) {
        Timber.e(e, "Auto background removal failed")
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private fun removeBackgroundManual(
    bitmap: Bitmap,
    seedPoints: List<PointF>,
    tolerance: Int
): Bitmap {
    try {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        // Get colors at seed points
        val seedColors = seedPoints.mapNotNull { point ->
            try {
                bitmap.getPixel(point.x.toInt(), point.y.toInt())
            } catch (e: Exception) {
                null
            }
        }

        if (seedColors.isEmpty()) return bitmap

        // Flood fill algorithm
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                try {
                    val pixel = bitmap.getPixel(x, y)

                    val matchesSeeds = seedColors.any { seedColor ->
                        colorDistance(pixel, seedColor) < tolerance
                    }

                    if (matchesSeeds) {
                        result.setPixel(x, y, Color.TRANSPARENT)
                    } else {
                        result.setPixel(x, y, pixel)
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }
        }

        return result
    } catch (e: Exception) {
        Timber.e(e, "Manual background removal failed")
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private fun removeShadows(bitmap: Bitmap): Bitmap {
    try {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        // Analyze image to find average brightness
        var totalBrightness = 0L
        var pixelCount = 0

        for (x in 0 until bitmap.width step 10) {
            for (y in 0 until bitmap.height step 10) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    totalBrightness += brightness
                    pixelCount++
                } catch (e: Exception) {
                    // Skip
                }
            }
        }

        val avgBrightness = if (pixelCount > 0) totalBrightness / pixelCount else 128

        // Adjust pixels based on brightness
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                try {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    val brightness = (r + g + b) / 3

                    // Boost darker areas (shadows)
                    if (brightness < avgBrightness) {
                        val boost = ((avgBrightness - brightness) * 0.6f).toInt()
                        val newR = (r + boost).coerceIn(0, 255)
                        val newG = (g + boost).coerceIn(0, 255)
                        val newB = (b + boost).coerceIn(0, 255)

                        result.setPixel(x, y, Color.rgb(newR, newG, newB))
                    } else {
                        result.setPixel(x, y, pixel)
                    }
                } catch (e: Exception) {
                    // Copy original
                    try {
                        result.setPixel(x, y, bitmap.getPixel(x, y))
                    } catch (e: Exception) {
                        // Skip
                    }
                }
            }
        }

        return result
    } catch (e: Exception) {
        Timber.e(e, "Shadow removal failed")
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private fun colorDistance(color1: Int, color2: Int): Int {
    val r1 = Color.red(color1)
    val g1 = Color.green(color1)
    val b1 = Color.blue(color1)

    val r2 = Color.red(color2)
    val g2 = Color.green(color2)
    val b2 = Color.blue(color2)

    val dr = r1 - r2
    val dg = g1 - g2
    val db = b1 - b2

    return sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
}