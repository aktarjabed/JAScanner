package aktarjabed.com.jaascanner.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.pdf.FillRectMark
import aktarjabed.com.jaascanner.pdf.Mark
import aktarjabed.com.jaascanner.pdf.NoteMark
import aktarjabed.com.jaascanner.pdf.NormPoint
import aktarjabed.com.jaascanner.pdf.Shapes
import aktarjabed.com.jaascanner.pdf.StampMark
import aktarjabed.com.jaascanner.pdf.StrokeMark
import aktarjabed.com.jaascanner.pdf.TextEditMark
import aktarjabed.com.jaascanner.pdf.TextFace
import aktarjabed.com.jaascanner.core.FontKind
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.ConfirmDialog
import aktarjabed.com.jaascanner.ui.components.PageView
import aktarjabed.com.jaascanner.ui.components.pinchToZoom
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val HIGHLIGHT_WIDTH_FRACTION = 0.028f
private const val HIGHLIGHT_ALPHA = 0.35f
private const val WHITE = 0xFFFFFFFF.toInt()
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

private val FREEHAND_TOOLS = setOf(EditorTool.Pen, EditorTool.Highlighter)
private val DRAG_TOOLS = setOf(
    EditorTool.Line,
    EditorTool.Arrow,
    EditorTool.Rectangle,
    EditorTool.Ellipse,
    EditorTool.HighlightBox,
    EditorTool.WhiteOut,
)
private val TAP_TOOLS = setOf(EditorTool.Signature, EditorTool.Text, EditorTool.Image, EditorTool.EditText, EditorTool.Note)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(app: AppViewModel, documentId: Long, screenId: Long) {
    val context = LocalContext.current
    val vm = containerViewModel { EditorViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val source = state.source
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var signatureDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setImageFromUri(context, uri)
    }
    val pickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val leave: () -> Unit = {
        if (state.marks.isEmpty()) {
            app.pop()
        } else {
            discardDialog = true
        }
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Edit and sign") },
                actions = {
                    TextButton(onClick = vm::undo, enabled = state.marks.isNotEmpty()) { Text("Undo") }
                    TextButton(onClick = vm::redo, enabled = state.undone.isNotEmpty()) { Text("Redo") }
                    TextButton(
                        onClick = { vm.save { newId -> app.replaceTopIfCurrent(screenId, Screen.Viewer(newId)) } },
                        enabled = state.marks.isNotEmpty() && !state.saving,
                    ) { Text("Save") }
                },
            )
        },
        bottomBar = {
            BottomAppBar(contentPadding = PaddingValues(horizontal = 4.dp)) {
                TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(MIN_ZOOM) }, modifier = Modifier.weight(1f)) { Text("Zoom -") }
                TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(MAX_ZOOM) }, modifier = Modifier.weight(1f)) { Text("Zoom +") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ToolBar(
                state = state,
                onTool = { tool ->
                    vm.setTool(tool)
                    if (tool == EditorTool.Signature && state.signature == null) signatureDialog = true
                    if (tool == EditorTool.Image && state.image == null) pickImage()
                },
                onPenColor = vm::setPenColor,
                onHighlightColor = vm::setHighlightColor,
                onPenWidth = vm::setPenWidth,
                onTextScale = vm::setTextScale,
                onText = vm::setText,
                onInsertDate = vm::insertDate,
                onChooseSignature = { signatureDialog = true },
                onChooseImage = pickImage,
                onFace = vm::setTextFace,
            )
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    source == null -> Text(
                        "This PDF cannot be edited. It may be password-protected or damaged.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> BoxWithConstraints(
                        Modifier.fillMaxSize().pinchToZoom { factor -> zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM) },
                    ) {
                        val pageWidth = maxWidth * zoom
                        Box(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight().width(pageWidth),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(count = source.pageCount, key = { it }) { index ->
                                    val pageMarks = state.marks.filter { it.page == index }.map { it.mark }
                                    PageView(source = source, index = index, width = pageWidth, night = false) {
                                        PageOverlay(
                                            tool = state.tool,
                                            marks = pageMarks,
                                            penColor = state.penColor,
                                            highlightColor = state.highlightColor,
                                            penWidth = state.penWidth,
                                            aspect = source.heightOverWidth(index),
                                            onMark = { vm.addMark(index, it) },
                                            onTap = { u, v -> vm.placeStamp(index, u, v) },
                                        )
                                    }
                                }
                            }
                        }
                        if (state.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    state.pendingNote?.let {
        NoteDialog(key = it, onConfirm = vm::confirmNote, onDismiss = vm::cancelNote)
    }
    state.pending?.let { pending ->
        EditTextDialog(
            pending = pending,
            onApply = vm::confirmEdit,
            onDismiss = vm::cancelEdit,
        )
    }
    if (signatureDialog) {
        SignatureDialog(
            onPick = {
                vm.setSignature(it)
                signatureDialog = false
            },
            onDismiss = { signatureDialog = false },
        )
    }
    if (discardDialog) {
        ConfirmDialog(
            title = "Discard your changes?",
            message = "You have marks that are not saved yet.",
            confirmLabel = "Discard",
            onConfirm = {
                discardDialog = false
                app.pop()
            },
            onDismiss = { discardDialog = false },
        )
    }
}

private val TOOL_LABELS = listOf(
    EditorTool.Hand to "Scroll",
    EditorTool.EditText to "Edit text",
    EditorTool.Pen to "Pen",
    EditorTool.Highlighter to "Highlight",
    EditorTool.HighlightBox to "Box highlight",
    EditorTool.Line to "Line",
    EditorTool.Arrow to "Arrow",
    EditorTool.Rectangle to "Rectangle",
    EditorTool.Ellipse to "Ellipse",
    EditorTool.WhiteOut to "White-out",
    EditorTool.Text to "Text",
    EditorTool.Note to "Note",
    EditorTool.Signature to "Sign",
    EditorTool.Image to "Picture",
)

@Composable
private fun ToolBar(
    state: EditorUiState,
    onTool: (EditorTool) -> Unit,
    onPenColor: (Int) -> Unit,
    onHighlightColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onTextScale: (Float) -> Unit,
    onText: (String) -> Unit,
    onInsertDate: () -> Unit,
    onChooseSignature: () -> Unit,
    onChooseImage: () -> Unit,
    onFace: (TextFace) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TOOL_LABELS.forEach { (entry, label) ->
                FilterChip(selected = state.tool == entry, onClick = { onTool(entry) }, label = { Text(label) })
            }
        }
        when (state.tool) {
            EditorTool.Pen, EditorTool.Line, EditorTool.Arrow, EditorTool.Rectangle, EditorTool.Ellipse -> Column {
                ColorRow(PEN_COLORS, state.penColor, onPenColor)
                ChoiceRow(PEN_WIDTHS, state.penWidth, onPenWidth)
                if (state.tool != EditorTool.Pen) Hint("Drag on the page to draw it.")
            }
            EditorTool.Highlighter -> ColorRow(HIGHLIGHT_COLORS, state.highlightColor, onHighlightColor)
            EditorTool.HighlightBox -> Column {
                ColorRow(HIGHLIGHT_COLORS, state.highlightColor, onHighlightColor)
                Hint("Drag a box over the text to highlight.")
            }
            EditorTool.WhiteOut -> Hint(
                "Drag a box to cover something with white. The hidden text stays inside the file, so this " +
                    "is not secure redaction.",
            )
            EditorTool.Signature -> Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.signature != null) "Tap the page to place your signature." else "Choose or draw a signature first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChooseSignature) { Text(if (state.signature != null) "Change" else "Choose") }
            }
            EditorTool.Image -> Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.image != null) "Tap the page to place the picture." else "Choose a picture (logo, stamp, photo) first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChooseImage) { Text(if (state.image != null) "Change" else "Choose") }
            }
            EditorTool.EditText -> Hint(
                "Tap a word on the page to change it. The app detects the font, size and colours. " +
                    "Letters the original font already contains are edited in the PDF's own text; " +
                    "anything else is redrawn in a matching font. Scanned pages have no editable text.",
            )
            EditorTool.Note -> Hint(
                "Tap the page where the note should sit, then type it. It is saved as a real sticky-note " +
                    "comment, so other PDF readers show it too.",
            )
            EditorTool.Text -> Column {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onText,
                    placeholder = { Text("Type text, then tap the page") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorRow(PEN_COLORS, state.penColor, onPenColor)
                    TextButton(onClick = onInsertDate) { Text("Today's date") }
                }
                ChoiceRow(TEXT_SCALES, state.textScale, onTextScale)
                FaceRow(state.textFace, onFace)
                if (state.recentTexts.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.recentTexts.forEach { recent ->
                            FilterChip(selected = false, onClick = { onText(recent) }, label = { Text(recent.take(24)) })
                        }
                    }
                }
            }
            EditorTool.Hand -> Hint("Pick a tool, then mark the page. Pinch to zoom. Your original file is never changed.")
        }
    }
}

@Composable
private fun FaceRow(selected: TextFace, onSelect: (TextFace) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextFace.entries.forEach { face ->
            FilterChip(selected = selected == face, onClick = { onSelect(face) }, label = { Text(face.label) })
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChoiceRow(options: List<Pair<Float, String>>, selected: Float, onSelect: (Float) -> Unit) {
    Row(
        Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { argb ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (argb == selected) 3.dp else 1.dp,
                        color = if (argb == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}

/** The mark that a drag from [a] to [b] creates with a shape tool; null for tools that are not shapes. */
private fun shapeMark(
    tool: EditorTool,
    a: NormPoint,
    b: NormPoint,
    penColor: Int,
    highlightColor: Int,
    penWidth: Float,
    aspect: Float,
): Mark? = when (tool) {
    EditorTool.Line -> StrokeMark(Shapes.line(a, b), penColor, penWidth)
    EditorTool.Arrow -> StrokeMark(Shapes.arrow(a, b, aspect), penColor, penWidth)
    EditorTool.Rectangle -> StrokeMark(Shapes.rectangle(a, b), penColor, penWidth)
    EditorTool.Ellipse -> StrokeMark(Shapes.ellipse(a, b), penColor, penWidth)
    EditorTool.HighlightBox -> FillRectMark(a.u, a.v, b.u, b.v, highlightColor, HIGHLIGHT_ALPHA)
    EditorTool.WhiteOut -> FillRectMark(a.u, a.v, b.u, b.v, WHITE)
    else -> null
}

/** Draws the page's marks and, depending on the tool, captures strokes, shape drags or taps. */
@Composable
private fun PageOverlay(
    tool: EditorTool,
    marks: List<Mark>,
    penColor: Int,
    highlightColor: Int,
    penWidth: Float,
    aspect: Float,
    onMark: (Mark) -> Unit,
    onTap: (Float, Float) -> Unit,
) {
    val live = remember { mutableStateListOf<NormPoint>() }
    var dragStart by remember { mutableStateOf<NormPoint?>(null) }
    var dragEnd by remember { mutableStateOf<NormPoint?>(null) }
    val highlighter = tool == EditorTool.Highlighter
    val strokeWidth = if (highlighter) HIGHLIGHT_WIDTH_FRACTION else penWidth

    val gestures: Modifier = when {
        tool in FREEHAND_TOOLS -> Modifier.pointerInput(tool, penColor, highlightColor, penWidth) {
            fun normalise(offset: Offset) = NormPoint(
                (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
            detectDragGestures(
                onDragStart = { offset ->
                    live.clear()
                    live.add(normalise(offset))
                },
                onDrag = { change, _ ->
                    change.consume()
                    live.add(normalise(change.position))
                },
                onDragEnd = {
                    if (live.isNotEmpty()) {
                        onMark(
                            StrokeMark(
                                points = live.toList(),
                                colorArgb = if (highlighter) highlightColor else penColor,
                                widthFraction = strokeWidth,
                                alpha = if (highlighter) HIGHLIGHT_ALPHA else 1f,
                            ),
                        )
                    }
                    live.clear()
                },
                onDragCancel = { live.clear() },
            )
        }
        tool in DRAG_TOOLS -> Modifier.pointerInput(tool, penColor, highlightColor, penWidth, aspect) {
            fun normalise(offset: Offset) = NormPoint(
                (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = normalise(offset)
                    dragEnd = dragStart
                },
                onDrag = { change, _ ->
                    change.consume()
                    dragEnd = normalise(change.position)
                },
                onDragEnd = {
                    val a = dragStart
                    val b = dragEnd
                    if (a != null && b != null && abs(b.u - a.u) + abs(b.v - a.v) > 0.01f) {
                        shapeMark(tool, a, b, penColor, highlightColor, penWidth, aspect)?.let(onMark)
                    }
                    dragStart = null
                    dragEnd = null
                },
                onDragCancel = {
                    dragStart = null
                    dragEnd = null
                },
            )
        }
        tool in TAP_TOOLS -> Modifier.pointerInput(tool) {
            detectTapGestures { offset ->
                onTap(
                    (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                )
            }
        }
        else -> Modifier
    }

    val previewStart = dragStart
    val previewEnd = dragEnd
    val preview = if (previewStart != null && previewEnd != null) {
        shapeMark(tool, previewStart, previewEnd, penColor, highlightColor, penWidth, aspect)
    } else {
        null
    }

    Canvas(Modifier.fillMaxSize().then(gestures)) {
        for (mark in marks) drawMark(mark)
        preview?.let { drawMark(it) }
        if (live.isNotEmpty()) {
            drawStrokePoints(
                points = live.toList(),
                colorArgb = if (highlighter) highlightColor else penColor,
                widthFraction = strokeWidth,
                alpha = if (highlighter) HIGHLIGHT_ALPHA else 1f,
            )
        }
    }
}

private fun DrawScope.drawMark(mark: Mark) {
    when (mark) {
        is StrokeMark -> drawStrokePoints(mark.points, mark.colorArgb, mark.widthFraction, mark.alpha)
        is FillRectMark -> {
            val left = min(mark.u0, mark.u1) * size.width
            val top = min(mark.v0, mark.v1) * size.height
            drawRect(
                color = Color(mark.colorArgb).copy(alpha = mark.alpha),
                topLeft = Offset(left, top),
                size = Size(abs(mark.u1 - mark.u0) * size.width, abs(mark.v1 - mark.v0) * size.height),
            )
        }
        is TextEditMark -> drawTextEditPreview(mark)
        is NoteMark -> drawNotePreview(mark)
        is StampMark -> {
            val width = mark.widthFraction * size.width
            val height = width * mark.bitmap.height / mark.bitmap.width
            drawImage(
                image = mark.bitmap.asImageBitmap(),
                dstOffset = IntOffset(
                    (mark.centerU * size.width - width / 2f).roundToInt(),
                    (mark.centerV * size.height - height / 2f).roundToInt(),
                ),
                dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            )
        }
    }
}

/** A small yellow note icon where the sticky note will sit. */
private fun DrawScope.drawNotePreview(mark: NoteMark) {
    val side = size.width * 0.04f
    val left = mark.u * size.width - side / 2f
    val top = mark.v * size.height - side / 2f
    drawRect(Color(0xFFFFD60A), topLeft = Offset(left, top), size = Size(side, side))
    drawRect(Color(0xFF3E2723), topLeft = Offset(left, top), size = Size(side, side), style = Stroke(width = 1.5f))
    for (row in 1..3) {
        val y = top + side * row / 4f
        drawLine(Color(0xFF3E2723), Offset(left + side * 0.2f, y), Offset(left + side * 0.8f, y), strokeWidth = 1.5f)
    }
}

private fun DrawScope.drawStrokePoints(points: List<NormPoint>, colorArgb: Int, widthFraction: Float, alpha: Float) {
    if (points.isEmpty()) return
    val width = (size.width * widthFraction).coerceAtLeast(1f)
    val color = Color(colorArgb).copy(alpha = alpha)
    if (points.size == 1) {
        drawCircle(color, radius = width / 2f, center = Offset(points[0].u * size.width, points[0].v * size.height))
        return
    }
    val path = Path()
    path.moveTo(points[0].u * size.width, points[0].v * size.height)
    for (i in 1 until points.size) path.lineTo(points[i].u * size.width, points[i].v * size.height)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** On-screen preview of an edited word: a patch in the page colour with the new text on top. */
private fun DrawScope.drawTextEditPreview(mark: TextEditMark) {
    val word = mark.word
    val pad = word.fontSizeFrac * size.width * 0.12f
    val left = word.u0 * size.width - pad
    val top = word.v0 * size.height
    drawRect(
        color = Color(mark.backgroundArgb),
        topLeft = Offset(left, top),
        size = Size((word.u1 - word.u0) * size.width + 2 * pad, (word.v1 - word.v0) * size.height),
    )
    val guess = aktarjabed.com.jaascanner.core.FontGuessing.guess(word.fontName)
    val face = mark.face
    val typeface = when {
        face != null -> face.typeface()
        guess.kind == FontKind.Serif -> android.graphics.Typeface.create("serif", styleOf(guess.bold, guess.italic))
        guess.kind == FontKind.Mono -> android.graphics.Typeface.create("monospace", styleOf(guess.bold, guess.italic))
        else -> android.graphics.Typeface.create("sans-serif", styleOf(guess.bold, guess.italic))
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = mark.textArgb
        this.typeface = typeface
        textSize = word.fontSizeFrac * size.width * (if (face?.handwriting == true) 1.15f else 1f)
    }
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(mark.newText, word.u0 * size.width, word.baselineV * size.height, paint) }
}

private fun styleOf(bold: Boolean, italic: Boolean): Int = when {
    bold && italic -> android.graphics.Typeface.BOLD_ITALIC
    bold -> android.graphics.Typeface.BOLD
    italic -> android.graphics.Typeface.ITALIC
    else -> android.graphics.Typeface.NORMAL
}

@Composable
private fun EditTextDialog(pending: PendingEdit, onApply: (String, TextFace?) -> Unit, onDismiss: () -> Unit) {
    var text by remember(pending) { mutableStateOf(pending.word.text) }
    var face by remember(pending) { mutableStateOf<TextFace?>(null) }
    val sizePt = pending.word.fontSizeFrac
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change this word") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Detected: ${pending.guess.label} (${pending.guess.cleanName}), about " +
                        "${(sizePt * PAGE_WIDTH_PT).roundToInt()} pt",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch(pending.textArgb)
                    Text("text colour", style = MaterialTheme.typography.bodySmall)
                    Swatch(pending.backgroundArgb)
                    Text("background", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = face == null, onClick = { face = null }, label = { Text("Match original") })
                    TextFace.entries.filter { it.handwriting }.forEach { option ->
                        FilterChip(selected = face == option, onClick = { face = option }, label = { Text(option.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(text, face) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Swatch(argb: Int) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** Typical page width in points, used only to show an approximate font size in the dialog. */
private const val PAGE_WIDTH_PT = 595f

@Composable
private fun NoteDialog(key: Any, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Keyed on the tapped spot: without this, dismissing a note unsent and tapping a different
    // spot to add another would show the first note's unsent text instead of a blank field.
    var text by rememberSaveable(key) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sticky note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                label = { Text("Your comment") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Add note") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
