package com.jascanner.presentation.editor.tools

import android.graphics.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.jascanner.domain.model.EditablePage
import com.jascanner.domain.model.ImageFilter
import com.jascanner.domain.model.ImageAdjustments
import com.jascanner.presentation.editor.tools.EditorUtils.drawImageLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun ImageEnhancementCanvas(
    page: EditablePage,
    onEnhancementApplied: (Bitmap, ImageAdjustments) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<ImageFilter?>(null) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var saturation by remember { mutableStateOf(1f) }
    var sharpness by remember { mutableStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val scope = rememberCoroutineScope()
    val originalBitmap = page.processedBitmap ?: page.originalBitmap ?: return

    // Generate preview when adjustments change
    LaunchedEffect(selectedFilter, brightness, contrast, saturation, sharpness) {
        if (!isProcessing) {
            isProcessing = true
            try {
                val preview = withContext(Dispatchers.Default) {
                    applyEnhancements(
                        originalBitmap,
                        selectedFilter,
                        brightness,
                        contrast,
                        saturation,
                        sharpness
                    )
                }
                previewBitmap = preview
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate preview")
            } finally {
                isProcessing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter selection
        FilterSelectionBar(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it }
        )

        // Adjustment controls
        AdjustmentControlsBar(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            sharpness = sharpness,
            onBrightnessChange = { brightness = it },
            onContrastChange = { contrast = it },
            onSaturationChange = { saturation = it },
            onSharpnessChange = { sharpness = it },
            onReset = {
                brightness = 0f
                contrast = 1f
                saturation = 1f
                sharpness = 0f
                selectedFilter = null
            },
            onApply = {
                scope.launch {
                    try {
                        val enhancedBitmap = withContext(Dispatchers.Default) {
                            applyEnhancements(
                                originalBitmap,
                                selectedFilter,
                                brightness,
                                contrast,
                                saturation,
                                sharpness
                            )
                        }

                        val adjustments = ImageAdjustments(
                            filter = selectedFilter,
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation,
                            sharpness = sharpness
                        )

                        onEnhancementApplied(enhancedBitmap, adjustments)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to apply enhancements")
                    }
                }
            }
        )

        // Preview canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val displayBitmap = previewBitmap ?: originalBitmap
                drawImageLayer(this, displayBitmap, size)
            }

            if (isProcessing) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun FilterSelectionBar(
    selectedFilter: ImageFilter?,
    onFilterSelected: (ImageFilter?) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onFilterSelected(null) },
                    label = { Text("None") }
                )

                ImageFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.displayName) },
                        leadingIcon = {
                            Icon(filter.icon, null, Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentControlsBar(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpness: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onSharpnessChange: (Float) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Adjustments",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            // Brightness
            AdjustmentSlider(
                label = "Brightness",
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -100f..100f,
                icon = Icons.Default.Brightness6
            )

            // Contrast
            AdjustmentSlider(
                label = "Contrast",
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..2f,
                icon = Icons.Default.Contrast,
                valueFormatter = { "%.1fx".format(it) }
            )

            // Saturation
            AdjustmentSlider(
                label = "Saturation",
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0f..2f,
                icon = Icons.Default.Palette,
                valueFormatter = { "%.1fx".format(it) }
            )

            // Sharpness
            AdjustmentSlider(
                label = "Sharpness",
                value = sharpness,
                onValueChange = onSharpnessChange,
                valueRange = -1f..1f,
                icon = Icons.Default.AutoFixHigh
            )

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

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueFormatter: (Float) -> String = { "%.0f".format(it) }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(
                    valueFormatter(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange
            )
        }
    }
}

// Image processing functions
private fun applyEnhancements(
    bitmap: Bitmap,
    filter: ImageFilter?,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpness: Float
): Bitmap {
    try {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Apply filter first
        filter?.let {
            result = applyFilter(result, it)
        }

        // Apply adjustments
        result = applyColorAdjustments(result, brightness, contrast, saturation)

        // Apply sharpness
        if (sharpness != 0f) {
            result = applySharpness(result, sharpness)
        }

        return result
    } catch (e: Exception) {
        Timber.e(e, "Enhancement failed")
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}

private fun applyFilter(bitmap: Bitmap, filter: ImageFilter): Bitmap {
    return when (filter) {
        ImageFilter.GRAYSCALE -> applyGrayscale(bitmap)
        ImageFilter.BLACK_AND_WHITE -> applyBlackAndWhite(bitmap)
        ImageFilter.WHITEBOARD -> applyWhiteboard(bitmap)
        ImageFilter.MAGIC_COLOR -> applyMagicColor(bitmap)
        ImageFilter.SEPIA -> applySepia(bitmap)
        ImageFilter.NEGATIVE -> applyNegative(bitmap)
    }
}

private fun applyGrayscale(bitmap: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()
    val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

private fun applyBlackAndWhite(bitmap: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val threshold = 128

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            try {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 +
                           Color.green(pixel) * 0.587 +
                           Color.blue(pixel) * 0.114).toInt()

                val newColor = if (gray > threshold) Color.WHITE else Color.BLACK
                result.setPixel(x, y, newColor)
            } catch (e: Exception) {
                // Skip problematic pixels
            }
        }
    }

    return result
}

private fun applyWhiteboard(bitmap: Bitmap): Bitmap {
    // Enhanced whiteboard filter - increases brightness and reduces shadows
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            try {
                val pixel = bitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)

                // Increase brightness
                r = (r * 1.2f).toInt().coerceIn(0, 255)
                g = (g * 1.2f).toInt().coerceIn(0, 255)
                b = (b * 1.2f).toInt().coerceIn(0, 255)

                // Reduce shadows - boost darker pixels more
                val avg = (r + g + b) / 3
                if (avg < 180) {
                    val boost = (180 - avg) * 0.5f
                    r = (r + boost).toInt().coerceIn(0, 255)
                    g = (g + boost).toInt().coerceIn(0, 255)
                    b = (b + boost).toInt().coerceIn(0, 255)
                }

                result.setPixel(x, y, Color.rgb(r, g, b))
            } catch (e: Exception) {
                // Skip
            }
        }
    }

    return result
}

private fun applyMagicColor(bitmap: Bitmap): Bitmap {
    // Auto color correction with histogram equalization
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

    // Calculate histograms
    val histR = IntArray(256)
    val histG = IntArray(256)
    val histB = IntArray(256)

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            try {
                val pixel = bitmap.getPixel(x, y)
                histR[Color.red(pixel)]++
                histG[Color.green(pixel)]++
                histB[Color.blue(pixel)]++
            } catch (e: Exception) {
                // Skip
            }
        }
    }

    // Calculate cumulative distribution
    val cdfR = IntArray(256)
    val cdfG = IntArray(256)
    val cdfB = IntArray(256)

    cdfR[0] = histR[0]
    cdfG[0] = histG[0]
    cdfB[0] = histB[0]

    for (i in 1 until 256) {
        cdfR[i] = cdfR[i - 1] + histR[i]
        cdfG[i] = cdfG[i - 1] + histG[i]
        cdfB[i] = cdfB[i - 1] + histB[i]
    }

    val totalPixels = bitmap.width * bitmap.height

    // Apply equalization
    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            try {
                val pixel = bitmap.getPixel(x, y)
                val r = ((cdfR[Color.red(pixel)] - cdfR[0]) * 255.0 / (totalPixels - cdfR[0])).toInt()
                val g = ((cdfG[Color.green(pixel)] - cdfG[0]) * 255.0 / (totalPixels - cdfG[0])).toInt()
                val b = ((cdfB[Color.blue(pixel)] - cdfB[0]) * 255.0 / (totalPixels - cdfB[0])).toInt()

                result.setPixel(
                    x, y,
                    Color.rgb(
                        r.coerceIn(0, 255),
                        g.coerceIn(0, 255),
                        b.coerceIn(0, 255)
                    )
                )
            } catch (e: Exception) {
                // Skip
            }
        }
    }

    return result
}

private fun applySepia(bitmap: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    val colorMatrix = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

private fun applyNegative(bitmap: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            try {
                val pixel = bitmap.getPixel(x, y)
                val r = 255 - Color.red(pixel)
                val g = 255 - Color.green(pixel)
                val b = 255 - Color.blue(pixel)
                result.setPixel(x, y, Color.rgb(r, g, b))
            } catch (e: Exception) {
                // Skip
            }
        }
    }

    return result
}

private fun applyColorAdjustments(
    bitmap: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float
): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    val colorMatrix = ColorMatrix().apply {
        // Apply saturation
        setSaturation(saturation)

        // Apply brightness and contrast
        val scale = contrast
        val translate = brightness

        postConcat(ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        ))
    }

    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

private fun applySharpness(bitmap: Bitmap, amount: Float): Bitmap {
    if (amount == 0f) return bitmap

    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

    // Sharpening kernel
    val kernel = if (amount > 0) {
        floatArrayOf(
            0f, -amount, 0f,
            -amount, 1 + 4 * amount, -amount,
            0f, -amount, 0f
        )
    } else {
        // Blur for negative sharpness
        val blur = -amount / 9
        floatArrayOf(
            blur, blur, blur,
            blur, 1 - 8 * blur, blur,
            blur, blur, blur
        )
    }

    for (x in 1 until bitmap.width - 1) {
        for (y in 1 until bitmap.height - 1) {
            try {
                var r = 0f
                var g = 0f
                var b = 0f

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val kernelValue = kernel[(ky + 1) * 3 + (kx + 1)]

                        r += Color.red(pixel) * kernelValue
                        g += Color.green(pixel) * kernelValue
                        b += Color.blue(pixel) * kernelValue
                    }
                }

                result.setPixel(
                    x, y,
                    Color.rgb(
                        r.toInt().coerceIn(0, 255),
                        g.toInt().coerceIn(0, 255),
                        b.toInt().coerceIn(0, 255)
                    )
                )
            } catch (e: Exception) {
                // Copy original pixel on error
                try {
                    result.setPixel(x, y, bitmap.getPixel(x, y))
                } catch (e: Exception) {
                    // Skip
                }
            }
        }
    }

    return result
}