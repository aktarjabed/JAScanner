package aktarjabed.com.jaascanner.ui.editor

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import aktarjabed.com.jaascanner.container
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val INK_COLOR = 0xFF1A237E.toInt()
private const val MAX_SIGNATURE_WIDTH_PX = 700f

@Composable
fun SignatureDialog(onPick: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val container = LocalContext.current.container
    val store = container.signatures
    val scope = rememberCoroutineScope()
    val inkWidth = with(LocalDensity.current) { 3.dp.toPx() }

    var saved by remember { mutableStateOf<List<Pair<File, Bitmap>>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    // Picking or saving a signature starts a coroutine; ignore taps until it has been handed over.
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        saved = store.list().mapNotNull { file -> store.load(file)?.let { bitmap -> file to bitmap } }
    }

    val strokes = remember { mutableStateListOf<List<Offset>>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved.isNotEmpty()) {
                    Text("Saved signatures", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(saved, key = { it.first.name }) { (file, bitmap) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Saved signature",
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(56.dp)
                                        .background(Color.White)
                                        .clickable(enabled = !picking) {
                                            picking = true
                                            onPick(bitmap)
                                        },
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            store.delete(file)
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            container.messenger.post("Could not delete that signature.")
                                        }
                                        reload++
                                    }
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
                Text("Draw a new one", style = MaterialTheme.typography.labelLarge)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> strokes.add(listOf(offset)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (strokes.isNotEmpty()) strokes[strokes.lastIndex] = strokes.last() + change.position
                                },
                            )
                        },
                ) {
                    strokes.forEach { points ->
                        if (points.size == 1) {
                            drawCircle(Color(INK_COLOR), radius = inkWidth / 2f, center = points[0])
                        } else {
                            val path = Path()
                            path.moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
                            drawPath(path, Color(INK_COLOR), style = Stroke(width = inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Row {
                    TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
                }
                Text(
                    "Signatures are kept only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty() && !picking,
                onClick = {
                    val bitmap = renderSignature(strokes.toList(), inkWidth)
                    if (bitmap != null) {
                        picking = true
                        scope.launch {
                            try {
                                store.save(bitmap)
                            } catch (error: CancellationException) {
                                picking = false
                                throw error
                            } catch (error: Exception) {
                                container.messenger.post("Could not save that signature, but it will still be used here.")
                            }
                            onPick(bitmap)
                        }
                    }
                },
            ) { Text("Use this signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Draws the strokes onto a tight, transparent bitmap. */
private fun renderSignature(strokes: List<List<Offset>>, inkWidth: Float): Bitmap? {
    val points = strokes.flatten()
    if (points.isEmpty()) return null
    val margin = inkWidth * 2f
    val minX = points.minOf { it.x } - margin
    val minY = points.minOf { it.y } - margin
    val width = (points.maxOf { it.x } + margin - minX).coerceAtLeast(1f)
    val height = (points.maxOf { it.y } + margin - minY).coerceAtLeast(1f)
    val scale = min(1f, MAX_SIGNATURE_WIDTH_PX / width)
    val bitmap = Bitmap.createBitmap(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(-minX, -minY)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = inkWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = INK_COLOR
    }
    for (stroke in strokes) {
        if (stroke.size == 1) {
            canvas.drawPoint(stroke[0].x, stroke[0].y, paint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(stroke[0].x, stroke[0].y)
            for (i in 1 until stroke.size) path.lineTo(stroke[i].x, stroke[i].y)
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
