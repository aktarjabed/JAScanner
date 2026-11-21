package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.FontGuess
import aktarjabed.com.jaascanner.core.FontKind
import aktarjabed.com.jaascanner.core.PageBox
import aktarjabed.com.jaascanner.core.PdfCoordinateMapper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.atan2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One line of recognised text. The box is given as fractions of the page as it is displayed:
 * u to the right and v downwards, both 0..1.
 */
data class RecognisedLine(
    val text: String,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
)

/** Result of embedding recognised text. [droppedCharacters] lists glyphs no available font could draw. */
data class TextLayerOutcome(
    val pagesWritten: Int = 0,
    val droppedCharacters: String = "",
) {
    val hasDroppedCharacters: Boolean get() = droppedCharacters.isNotEmpty()
}

/**
 * Makes a scanned PDF searchable and copyable by adding the recognised text as an invisible layer
 * (text rendering mode 3) right on top of the scanned picture. The page looks exactly as before;
 * search, selection and copy in any PDF reader now find the words.
 */
object PdfTextLayer {

    private const val BASELINE_FRACTION = 0.8f
    private const val SIZE_OF_BOX = 0.8f
    private const val MIN_SIZE_PT = 2f
    private const val MAX_SIZE_PT = 300f

    /** Writes [target] with the text layer added; reports pages written and glyphs dropped. */
    suspend fun add(source: File, target: File, lines: Map<Int, List<RecognisedLine>>): TextLayerOutcome =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    val outcome = addTo(document, lines)
                    document.save(target)
                    outcome
                }
            } catch (error: CancellationException) {
                target.delete()
                throw error
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }

    /** Adds the layer to an open document. Pages outside the document are ignored. */
    fun addTo(document: PDDocument, lines: Map<Int, List<RecognisedLine>>): TextLayerOutcome {
        val fonts = HashMap<String, PDFont>()
        val dropped = LinkedHashSet<Char>()
        val writtenPages = HashSet<Int>()
        for ((pageIndex, pageLines) in lines) {
            if (pageIndex !in 0 until document.numberOfPages) continue
            val usable = pageLines.filter { it.text.isNotBlank() && it.u1 > it.u0 && it.v1 > it.v0 }
            if (usable.isEmpty()) continue
            val page = document.getPage(pageIndex)
            val crop = page.cropBox
            val box = PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
            val rotation = page.rotation
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                for (line in usable) {
                    val result = writeLine(document, content, box, rotation, line, fonts)
                    if (result.written) writtenPages.add(pageIndex)
                    dropped.addAll(result.dropped)
                }
            }
        }
        return TextLayerOutcome(writtenPages.size, dropped.joinToString(""))
    }

    private data class LineOutcome(val written: Boolean, val dropped: Set<Char> = emptySet())

    private data class FontChoice(val font: PDFont, val drawn: String, val dropped: Set<Char>)

    private fun writeLine(
        document: PDDocument,
        content: PDPageContentStream,
        box: PageBox,
        rotation: Int,
        line: RecognisedLine,
        fonts: HashMap<String, PDFont>,
    ): LineOutcome {
        val cleaned = line.text.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ').trim()
        if (cleaned.isEmpty()) return LineOutcome(false)

        // Everything that can fail is done before the first operator is written, so a bad line can
        // never leave half a text object in the page.
        val choice = chooseFont(document, cleaned, fonts) ?: return LineOutcome(false, cleaned.filter { !it.isWhitespace() }.toSet())
        val font = choice.font
        val drawn = choice.drawn
        if (drawn.isEmpty()) return LineOutcome(false, choice.dropped)

        val displayedWidth = PdfCoordinateMapper.displayedWidth(box, rotation)
        val displayedHeight = if (PdfCoordinateMapper.normalizeRotation(rotation) % 180 == 0) box.height else box.width
        val sizePt = ((line.v1 - line.v0) * displayedHeight * SIZE_OF_BOX).coerceIn(MIN_SIZE_PT, MAX_SIZE_PT)
        val naturalWidth = runCatching { font.getStringWidth(drawn) / 1000f * sizePt }.getOrNull() ?: return LineOutcome(false, choice.dropped)
        if (naturalWidth <= 0f) return LineOutcome(false, choice.dropped)
        val stretch = ((line.u1 - line.u0) * displayedWidth / naturalWidth).coerceIn(0.05f, 20f)

        val baselineV = line.v0 + (line.v1 - line.v0) * BASELINE_FRACTION
        val origin = PdfCoordinateMapper.point(box, rotation, line.u0, baselineV)
        val along = PdfCoordinateMapper.point(box, rotation, line.u0 + 0.1f, baselineV)
        val angle = atan2((along.y - origin.y).toDouble(), (along.x - origin.x).toDouble())

        content.saveGraphicsState()
        content.transform(Matrix.getRotateInstance(angle, origin.x, origin.y))
        content.beginText()
        content.setRenderingMode(RenderingMode.NEITHER)
        content.setFont(font, sizePt)
        content.setTextMatrix(Matrix(stretch, 0f, 0f, 1f, 0f, 0f))
        content.showText(drawn)
        content.endText()
        content.restoreGraphicsState()
        return LineOutcome(true, choice.dropped)
    }

    /**
     * A font that can encode the whole line (standard Helvetica, else one of the phone's fonts), or
     * failing that Helvetica with the letters it cannot draw removed. Dropped letters are reported
     * instead of vanishing silently.
     */
    private fun chooseFont(document: PDDocument, text: String, fonts: HashMap<String, PDFont>): FontChoice? {
        val guess = FontGuess(FontKind.Sans, bold = false, italic = false, cleanName = "Helvetica")
        SystemFonts.pick(document, guess, text, fonts)?.let { return FontChoice(it, text, emptySet()) }

        val helvetica = PDType1Font.HELVETICA
        val kept = StringBuilder()
        val dropped = LinkedHashSet<Char>()
        for (ch in text) {
            when {
                ch.isWhitespace() -> kept.append(' ')
                runCatching { helvetica.encode(ch.toString()) }.isSuccess -> kept.append(ch)
                else -> dropped.add(ch)
            }
        }
        val drawn = kept.toString().trim()
        return if (drawn.isEmpty()) null else FontChoice(helvetica, drawn, dropped)
    }
}
