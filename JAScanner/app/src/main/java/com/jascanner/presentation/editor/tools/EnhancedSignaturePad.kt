package com.jascanner.presentation.editor.tools

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jascanner.domain.model.SavedSignature
import com.jascanner.domain.model.SignatureData
import com.jascanner.presentation.editor.tools.EditorUtils.ColorButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun EnhancedSignaturePad(
    onSignatureSaved: (SignatureData) -> Unit,
    onDismiss: () -> Unit
) {
    var paths by remember { mutableStateOf<List<List<PointF>>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var strokeWidth by remember { mutableStateOf(3f) }
    var strokeColor by remember { mutableStateOf(android.graphics.Color.BLACK) }
    var showSavedSignatures by remember { mutableStateOf(false) }
    var savedSignatures by remember { mutableStateOf<List<SavedSignature>>(emptyList()) }
    var signerName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load saved signatures
    LaunchedEffect(Unit) {
        try {
            savedSignatures = loadSavedSignatures(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load saved signatures")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Create Signature",
                        style = MaterialTheme.typography.titleLarge
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Signer name input
                OutlinedTextField(
                    value = signerName,
                    onValueChange = { signerName = it },
                    label = { Text("Signer Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                // Controls
                SignaturePadControls(
                    strokeWidth = strokeWidth,
                    strokeColor = strokeColor,
                    onStrokeWidthChange = { strokeWidth = it },
                    onStrokeColorChange = { strokeColor = it },
                    onClear = {
                        paths = emptyList()
                        currentPath = emptyList()
                    },
                    onUndo = {
                        if (paths.isNotEmpty()) {
                            paths = paths.dropLast(1)
                        }
                    },
                    onToggleSavedSignatures = {
                        showSavedSignatures = !showSavedSignatures
                    }
                )

                Spacer(Modifier.height(8.dp))

                // Signature drawing area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White, MaterialTheme.shapes.medium)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = listOf(PointF(offset.x, offset.y))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPath = currentPath + PointF(
                                            change.position.x,
                                            change.position.y
                                        )
                                    },
                                    onDragEnd = {
                                        if (currentPath.isNotEmpty()) {
                                            paths = paths + listOf(currentPath)
                                        }
                                        currentPath = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Draw completed paths
                        paths.forEach { path ->
                            if (path.size > 1) {
                                val graphicsPath = Path().apply {
                                    moveTo(path.first().x, path.first().y)
                                    path.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = graphicsPath,
                                    color = Color(strokeColor),
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Draw current path
                        if (currentPath.size > 1) {
                            val graphicsPath = Path().apply {
                                moveTo(currentPath.first().x, currentPath.first().y)
                                currentPath.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = graphicsPath,
                                color = Color(strokeColor),
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw baseline guide
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, size.height * 0.7f),
                            end = Offset(size.width, size.height * 0.7f),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    if (paths.isEmpty() && currentPath.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sign here",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Saved signatures
                if (showSavedSignatures && savedSignatures.isNotEmpty()) {
                    SavedSignaturesRow(
                        signatures = savedSignatures,
                        onSignatureSelected = { signature ->
                            paths = signature.paths
                            signerName = signature.signerName ?: ""
                        },
                        onSignatureDeleted = { signature ->
                            scope.launch {
                                try {
                                    deleteSignature(context, signature.id)
                                    savedSignatures = savedSignatures.filter { it.id != signature.id }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to delete signature")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                try {
                                    val signatureBitmap = createSignatureBitmap(
                                        paths,
                                        strokeColor,
                                        strokeWidth
                                    )

                                    val signatureData = SignatureData(
                                        id = UUID.randomUUID().toString(),
                                        paths = paths,
                                        bitmap = signatureBitmap,
                                        timestamp = System.currentTimeMillis(),
                                        signerName = signerName.takeIf { it.isNotBlank() }
                                    )

                                    // Save for later use
                                    saveSignature(context, signatureData)

                                    onSignatureSaved(signatureData)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to create signature")
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = paths.isNotEmpty() && !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, null)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Save & Use")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignaturePadControls(
    strokeWidth: Float,
    strokeColor: Int,
    onStrokeWidthChange: (Float) -> Unit,
    onStrokeColorChange: (Int) -> Unit,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    onToggleSavedSignatures: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stroke controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color options
            listOf(
                android.graphics.Color.BLACK,
                android.graphics.Color.BLUE,
                android.graphics.Color.RED
            ).forEach { color ->
                ColorButton(
                    color = color,
                    isSelected = strokeColor == color,
                    onClick = { onStrokeColorChange(color) }
                )
            }

            VerticalDivider(modifier = Modifier.height(30.dp))

            // Width selector
            Text("Width:", style = MaterialTheme.typography.labelSmall)
            listOf(2f, 3f, 5f).forEach { width ->
                FilterChip(
                    selected = strokeWidth == width,
                    onClick = { onStrokeWidthChange(width) },
                    label = { Text("${width.toInt()}") }
                )
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onToggleSavedSignatures) {
                Icon(Icons.Default.FolderOpen, "Saved Signatures")
            }

            IconButton(onClick = onUndo) {
                Icon(Icons.Default.Undo, "Undo")
            }

            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, "Clear")
            }
        }
    }
}

@Composable
private fun SavedSignaturesRow(
    signatures: List<SavedSignature>,
    onSignatureSelected: (SavedSignature) -> Unit,
    onSignatureDeleted: (SavedSignature) -> Unit
) {
    Column {
        Text(
            "Saved Signatures",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(4.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(signatures) { signature ->
                SavedSignatureItem(
                    signature = signature,
                    onSelect = { onSignatureSelected(signature) },
                    onDelete = { onSignatureDeleted(signature) }
                )
            }
        }
    }
}

@Composable
private fun SavedSignatureItem(
    signature: SavedSignature,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .width(150.dp)
            .height(80.dp)
    ) {
        Box {
            signature.bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Saved Signature",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    signature.signerName ?: "Signature",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )

                Text(
                    "${signature.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Helper functions

private suspend fun createSignatureBitmap(
    paths: List<List<PointF>>,
    strokeColor: Int,
    strokeWidth: Float
): Bitmap = withContext(Dispatchers.Default) {
    try {
        val width = 400
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        val paint = android.graphics.Paint().apply {
            color = strokeColor
            this.strokeWidth = strokeWidth
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        paths.forEach { path ->
            if (path.size > 1) {
                val androidPath = android.graphics.Path().apply {
                    moveTo(path.first().x, path.first().y)
                    path.drop(1).forEach { lineTo(it.x, it.y) }
                }
                canvas.drawPath(androidPath, paint)
            }
        }

        bitmap
    } catch (e: Exception) {
        Timber.e(e, "Failed to create signature bitmap")
        Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    }
}

private suspend fun saveSignature(context: android.content.Context, signatureData: SignatureData) =
    withContext(Dispatchers.IO) {
        try {
            val signaturesDir = File(context.filesDir, "signatures")
            signaturesDir.mkdirs()

            val file = File(signaturesDir, "${signatureData.id}.png")
            FileOutputStream(file).use { out ->
                signatureData.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Timber.d("Signature saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save signature")
            throw e
        }
    }

private suspend fun loadSavedSignatures(context: android.content.Context): List<SavedSignature> =
    withContext(Dispatchers.IO) {
        try {
            val signaturesDir = File(context.filesDir, "signatures")
            if (!signaturesDir.exists()) return@withContext emptyList()

            signaturesDir.listFiles()
                ?.filter { it.extension == "png" }
                ?.mapNotNull { file ->
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        SavedSignature(
                            id = file.nameWithoutExtension,
                            paths = emptyList(), // Load from metadata if stored
                            timestamp = file.lastModified(),
                            signerName = null, // Load from metadata if stored
                            bitmap = bitmap
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load signature bitmap for ${file.name}")
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load saved signatures")
            emptyList()
        }
    }

private suspend fun deleteSignature(context: android.content.Context, signatureId: String) =
    withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "signatures/$signatureId.png")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete signature")
            throw e
        }
    }