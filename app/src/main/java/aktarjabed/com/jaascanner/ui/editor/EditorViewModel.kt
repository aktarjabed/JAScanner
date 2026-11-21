package aktarjabed.com.jaascanner.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.ColorSampling
import aktarjabed.com.jaascanner.core.DetectedColors
import aktarjabed.com.jaascanner.core.FontGuess
import aktarjabed.com.jaascanner.core.FontGuessing
import aktarjabed.com.jaascanner.data.EditorMemory
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.ImageDecoding
import aktarjabed.com.jaascanner.pdf.Mark
import aktarjabed.com.jaascanner.pdf.NoteMark
import aktarjabed.com.jaascanner.pdf.PdfEditor
import aktarjabed.com.jaascanner.pdf.PdfPageSource
import aktarjabed.com.jaascanner.pdf.PdfTextReplace
import aktarjabed.com.jaascanner.pdf.PdfTextRuns
import aktarjabed.com.jaascanner.pdf.StampMark
import aktarjabed.com.jaascanner.pdf.TextBitmaps
import aktarjabed.com.jaascanner.pdf.TextEditMark
import aktarjabed.com.jaascanner.pdf.TextFace
import aktarjabed.com.jaascanner.pdf.TextWord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import aktarjabed.com.jaascanner.pdf.toUserMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTool {
    Hand,
    Pen,
    Highlighter,
    Line,
    Arrow,
    Rectangle,
    Ellipse,
    HighlightBox,
    WhiteOut,
    Signature,
    Text,
    Image,
    EditText,
    Note,
}

/** A word the user tapped in Edit text mode, with what was detected about it. */
data class PendingEdit(
    val word: TextWord,
    val guess: FontGuess,
    val textArgb: Int,
    val backgroundArgb: Int,
)

/** Where the user tapped with the Note tool, waiting for the note's text. */
data class PendingNote(val page: Int, val u: Float, val v: Float)

data class PlacedMark(val page: Int, val mark: Mark)

val PEN_COLORS = listOf(0xFF111111.toInt(), 0xFF1A237E.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt())
val HIGHLIGHT_COLORS = listOf(0xFFFFEB3B.toInt(), 0xFFB2FF59.toInt(), 0xFFFF80AB.toInt(), 0xFFFFAB40.toInt())

/** Line widths as a fraction of the page width, with the label shown to the user. */
val PEN_WIDTHS = listOf(0.0022f to "Thin", 0.004f to "Medium", 0.009f to "Thick")
val TEXT_SCALES = listOf(0.7f to "Small", 1f to "Medium", 1.6f to "Large")

data class EditorUiState(
    val document: DocumentEntity? = null,
    val source: PdfPageSource? = null,
    val loading: Boolean = true,
    val tool: EditorTool = EditorTool.Hand,
    val penColor: Int = PEN_COLORS[1],
    val highlightColor: Int = HIGHLIGHT_COLORS[0],
    val penWidth: Float = PEN_WIDTHS[1].first,
    val textScale: Float = 1f,
    val marks: List<PlacedMark> = emptyList(),
    val undone: List<PlacedMark> = emptyList(),
    val signature: Bitmap? = null,
    val image: Bitmap? = null,
    val text: String = "",
    val textFace: TextFace = TextFace.Print,
    val recentTexts: List<String> = emptyList(),
    val pending: PendingEdit? = null,
    val pendingNote: PendingNote? = null,
    val saving: Boolean = false,
)

class EditorViewModel(
    private val container: AppContainer,
    private val documentId: Long,
) : ViewModel() {

    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val document = container.documents.get(documentId)
            val source = document?.let { PdfPageSource.open(container.documents.fileOf(it)) }
            mutableState.update { it.copy(document = document, source = source, loading = false) }
        }
        viewModelScope.launch {
            val memory = container.settings.editorMemory.first()
            mutableState.update { current ->
                current.copy(
                    penColor = memory.penColor ?: current.penColor,
                    highlightColor = memory.highlightColor ?: current.highlightColor,
                    penWidth = memory.penWidth ?: current.penWidth,
                    textScale = memory.textScale ?: current.textScale,
                    textFace = TextFace.entries.firstOrNull { it.name == memory.textFace } ?: current.textFace,
                    recentTexts = memory.recentTexts,
                )
            }
        }
    }

    private val wordCache = HashMap<Int, List<TextWord>>()

    private fun persist() {
        val current = mutableState.value
        val memory = EditorMemory(
            penColor = current.penColor,
            highlightColor = current.highlightColor,
            penWidth = current.penWidth,
            textScale = current.textScale,
            textFace = current.textFace.name,
            recentTexts = current.recentTexts,
        )
        container.appScope.launch { container.settings.saveEditorMemory(memory) }
    }

    private fun rememberText(text: String) {
        val clean = text.trim().take(MAX_REMEMBERED_LENGTH)
        if (clean.isEmpty()) return
        mutableState.update { it.copy(recentTexts = (listOf(clean) + it.recentTexts.filter { old -> old != clean }).take(MAX_RECENT)) }
        persist()
    }

    fun setTextFace(face: TextFace) {
        mutableState.update { it.copy(textFace = face) }
        persist()
    }

    fun setTool(tool: EditorTool) = mutableState.update { it.copy(tool = tool) }

    fun setPenColor(color: Int) {
        mutableState.update { it.copy(penColor = color) }
        persist()
    }

    fun setHighlightColor(color: Int) {
        mutableState.update { it.copy(highlightColor = color) }
        persist()
    }

    fun setPenWidth(width: Float) {
        mutableState.update { it.copy(penWidth = width) }
        persist()
    }

    fun setTextScale(scale: Float) {
        mutableState.update { it.copy(textScale = scale) }
        persist()
    }

    fun setText(text: String) = mutableState.update { it.copy(text = text) }

    fun insertDate() = setText(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))

    fun setSignature(bitmap: Bitmap) = mutableState.update { it.copy(signature = bitmap, tool = EditorTool.Signature) }

    /** Loads a picture (logo, photo, stamp) to place on pages. */
    fun setImageFromUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ImageDecoding.decode(appContext, uri, maxSide = 1600) }.getOrNull()
            }
            if (bitmap == null) {
                container.messenger.post("That picture could not be read.")
            } else {
                mutableState.update { it.copy(image = bitmap, tool = EditorTool.Image) }
            }
        }
    }

    fun addMark(page: Int, mark: Mark) {
        mutableState.update { it.copy(marks = it.marks + PlacedMark(page, mark), undone = emptyList()) }
    }

    fun undo() {
        mutableState.update { current ->
            val last = current.marks.lastOrNull() ?: return@update current
            current.copy(marks = current.marks.dropLast(1), undone = current.undone + last)
        }
    }

    fun redo() {
        mutableState.update { current ->
            val last = current.undone.lastOrNull() ?: return@update current
            current.copy(marks = current.marks + last, undone = current.undone.dropLast(1))
        }
    }

    /** Places the current signature, picture or text, centred on the tapped spot (u, v are 0..1 on the page). */
    fun placeStamp(page: Int, u: Float, v: Float) {
        val current = mutableState.value
        val stamp: StampMark = when (current.tool) {
            EditorTool.Signature -> {
                val bitmap = current.signature ?: return
                StampMark(bitmap, u, v, widthFraction = SIGNATURE_WIDTH_FRACTION)
            }
            EditorTool.Image -> {
                val bitmap = current.image ?: return
                StampMark(bitmap, u, v, widthFraction = IMAGE_WIDTH_FRACTION)
            }
            EditorTool.Text -> {
                if (current.text.isBlank()) {
                    container.messenger.post("Type the text first, then tap the page.")
                    return
                }
                val bitmap = TextBitmaps.render(current.text, current.penColor, face = current.textFace)
                rememberText(current.text)
                val lines = current.text.lines().size
                val widthFraction = (bitmap.width.toFloat() / bitmap.height) * TEXT_HEIGHT_FRACTION * current.textScale * lines
                StampMark(bitmap, u, v, widthFraction = widthFraction.coerceIn(0.05f, 0.95f))
            }
            EditorTool.EditText -> {
                pickWord(page, u, v)
                return
            }
            EditorTool.Note -> {
                mutableState.update { it.copy(pendingNote = PendingNote(page, u, v)) }
                return
            }
            else -> return
        }
        addMark(page, stamp)
    }

    /** Finds the real text under a tap and detects its font, size and colours. */
    private fun pickWord(page: Int, u: Float, v: Float) {
        val current = mutableState.value
        val document = current.document ?: return
        val source = current.source ?: return
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val words = wordCache[page] ?: PDDocument.load(container.documents.fileOf(document)).use {
                        PdfTextRuns.analyse(it, page).words
                    }.also { wordCache[page] = it }
                    PdfTextRuns.wordAt(words, u, v)
                }.getOrNull()
            }
            if (picked == null) {
                container.messenger.post(
                    "No editable text there. Scanned pages have no real text; use White-out and Text instead.",
                )
                return@launch
            }
            val colors = detectColors(source, picked)
            mutableState.update {
                it.copy(
                    pending = PendingEdit(picked, FontGuessing.guess(picked.fontName), colors.textArgb, colors.backgroundArgb),
                )
            }
        }
    }

    private suspend fun detectColors(source: PdfPageSource, word: TextWord): DetectedColors {
        val bitmap = source.render(word.page, COLOR_SAMPLE_WIDTH)
            ?: return DetectedColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), false)
        return withContext(Dispatchers.Default) {
            val margin = COLOR_SAMPLE_MARGIN
            val left = (word.u0 * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val top = (word.v0 * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val right = (word.u1 * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (word.v1 * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
            val cropLeft = (left - margin).coerceAtLeast(0)
            val cropTop = (top - margin).coerceAtLeast(0)
            val cropRight = (right + margin).coerceAtMost(bitmap.width)
            val cropBottom = (bottom + margin).coerceAtMost(bitmap.height)
            val width = cropRight - cropLeft
            val height = cropBottom - cropTop
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, cropLeft, cropTop, width, height)
            ColorSampling.detect(pixels, width, height, left - cropLeft, top - cropTop, right - cropLeft, bottom - cropTop)
        }
    }

    fun cancelEdit() = mutableState.update { it.copy(pending = null) }

    fun cancelNote() = mutableState.update { it.copy(pendingNote = null) }

    /** Adds the sticky note the user just typed at the spot they tapped. */
    fun confirmNote(text: String) {
        val spot = mutableState.value.pendingNote ?: return
        val clean = text.trim()
        mutableState.update { it.copy(pendingNote = null) }
        if (clean.isNotEmpty()) addMark(spot.page, NoteMark(spot.u, spot.v, clean.take(aktarjabed.com.jaascanner.pdf.PdfNotes.MAX_TEXT)))
    }

    /**
     * Adds the replacement to the page. [face] null keeps the original font; the message says
     * whether the original text will be edited in place or covered with a look-alike.
     */
    fun confirmEdit(newText: String, face: TextFace?) {
        val current = mutableState.value
        val pending = current.pending ?: return
        val document = current.document ?: return
        val clean = newText.trim()
        if (clean.isEmpty() || clean == pending.word.text) {
            mutableState.update { it.copy(pending = null) }
            return
        }
        // Clear it now, synchronously, not at the end of the coroutine below: otherwise a second
        // tap on "Apply" before the first confirm finishes would still see a non-null `pending`
        // and launch a second coroutine that adds a duplicate mark for the same edit.
        mutableState.update { it.copy(pending = null) }
        viewModelScope.launch {
            val outcome = if (face == null) {
                withContext(Dispatchers.IO) {
                    runCatching { PdfTextReplace.check(container.documents.fileOf(document), pending.word, clean) }
                        .getOrElse { PdfTextReplace.Outcome.Unsupported("could not analyse the page") }
                }
            } else {
                null
            }
            val message = when (outcome) {
                null -> "Replaced with a ${face?.label?.lowercase()} handwriting-style font."
                PdfTextReplace.Outcome.Done -> "Edits the PDF's own text, in its original font."
                is PdfTextReplace.Outcome.Unsupported ->
                    "The original font cannot draw this (${outcome.reason}). It will be covered and redrawn in a matching font."
            }
            addMark(
                pending.word.page,
                TextEditMark(pending.word, clean, pending.textArgb, pending.backgroundArgb, face),
            )
            rememberText(clean)
            container.messenger.post(message)
        }
    }

    /** Writes a new PDF with every mark baked in; the original document is left untouched. */
    fun save(onSaved: (Long) -> Unit) {
        val current = mutableState.value
        val document = current.document ?: return
        if (current.marks.isEmpty() || current.saving) return
        mutableState.update { it.copy(saving = true) }
        container.appScope.launch {
            val target = container.documents.newOutputFile()
            try {
                val grouped = current.marks.groupBy({ it.page }, { it.mark })
                val report = PdfEditor.apply(container.documents.fileOf(document), target, grouped)
                val saved = container.documents.registerOutput(
                    file = target,
                    name = document.name + " (edited)",
                    source = DocumentEntity.SOURCE_TOOL,
                    folderId = document.folderId,
                )
                if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                val textNote = when {
                    report.editedInPlace + report.overlaid + report.skipped == 0 -> ""
                    else -> " Text edits: ${report.editedInPlace} in the original font, ${report.overlaid} redrawn" +
                        if (report.skipped > 0) ", ${report.skipped} could not be drawn." else "."
                }
                container.messenger.post("Saved as a new copy. The original is unchanged.$textNote")
                withContext(Dispatchers.Main) { onSaved(saved.id) }
            } catch (error: Throwable) {
                target.delete()
                container.messenger.post(error.toUserMessage())
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        val source = mutableState.value.source ?: return
        container.appScope.launch { source.release() }
    }

    private companion object {
        const val SIGNATURE_WIDTH_FRACTION = 0.3f
        const val IMAGE_WIDTH_FRACTION = 0.3f
        const val TEXT_HEIGHT_FRACTION = 0.035f
        const val MAX_RECENT = 12
        const val MAX_REMEMBERED_LENGTH = 80
        const val COLOR_SAMPLE_WIDTH = 1400
        const val COLOR_SAMPLE_MARGIN = 3
    }
}
