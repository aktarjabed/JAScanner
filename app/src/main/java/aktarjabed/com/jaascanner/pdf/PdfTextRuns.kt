package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

/**
 * One word of real (selectable) text on a page.
 * Boxes are fractions of the page as displayed: u to the right, v downwards.
 */
data class TextWord(
    val text: String,
    val page: Int,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val baselineV: Float,
    /** Font size as a fraction of the displayed page width. */
    val fontSizeFrac: Float,
    val fontName: String,
    /** Where the word starts and ends inside [PageText.flat]. */
    val flatStart: Int,
    val flatEnd: Int,
)

/** All text of one page in content-stream order. [flat] is every glyph's text joined together. */
class PageText(val words: List<TextWord>, val flat: String)

object PdfTextRuns {

    private const val ASCENT = 0.80f
    private const val DESCENT = 0.22f
    private const val GAP_FRACTION = 0.28f

    private class Collector : PDFTextStripper() {
        val glyphs = ArrayList<TextPosition>()

        override fun processTextPosition(text: TextPosition) {
            glyphs.add(text)
        }
    }

    fun analyse(document: PDDocument, pageIndex: Int): PageText {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox
        val turned = ((page.rotation % 360) + 360) % 360 % 180 != 0
        val width = if (turned) crop.height else crop.width
        val height = if (turned) crop.width else crop.height
        val collector = Collector()
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(document)

        val flat = StringBuilder()
        val words = ArrayList<TextWord>()
        var current = ArrayList<TextPosition>()
        var currentStart = 0
        var previous: TextPosition? = null

        fun flush() {
            if (current.isEmpty()) return
            val first = current.first()
            val last = current.last()
            val size = first.fontSizeInPt.coerceAtLeast(1f)
            val left = first.xDirAdj
            val right = last.xDirAdj + last.widthDirAdj
            val baseline = first.yDirAdj
            val text = current.joinToString("") { it.unicode ?: "" }
            if (text.isNotBlank() && width > 0f && height > 0f) {
                words.add(
                    TextWord(
                        text = text,
                        page = pageIndex,
                        u0 = (left / width).coerceIn(0f, 1f),
                        v0 = ((baseline - size * ASCENT) / height).coerceIn(0f, 1f),
                        u1 = (right / width).coerceIn(0f, 1f),
                        v1 = ((baseline + size * DESCENT) / height).coerceIn(0f, 1f),
                        baselineV = (baseline / height).coerceIn(0f, 1f),
                        fontSizeFrac = size / width,
                        fontName = first.font?.name ?: "",
                        flatStart = currentStart,
                        flatEnd = currentStart + text.length,
                    ),
                )
            }
            current = ArrayList()
        }

        for (glyph in collector.glyphs) {
            val unicode = glyph.unicode ?: ""
            val size = glyph.fontSizeInPt.coerceAtLeast(1f)
            val startsNewWord = previous?.let { before ->
                val sameLine = abs(glyph.yDirAdj - before.yDirAdj) <= size * 0.5f
                val gap = glyph.xDirAdj - (before.xDirAdj + before.widthDirAdj)
                !sameLine || gap > size * GAP_FRACTION || gap < -size
            } ?: false
            if (unicode.isBlank() || startsNewWord || glyph.dir != 0f) flush()
            if (unicode.isNotBlank() && glyph.dir == 0f) {
                if (current.isEmpty()) currentStart = flat.length
                current.add(glyph)
            }
            flat.append(unicode)
            previous = glyph
        }
        flush()
        return PageText(words, flat.toString())
    }

    /** The word under a tap, or the nearest one within a small distance. */
    fun wordAt(words: List<TextWord>, u: Float, v: Float, tolerance: Float = 0.02f): TextWord? {
        words.firstOrNull { u in it.u0..it.u1 && v in it.v0..it.v1 }?.let { return it }
        return words
            .map { word ->
                val dx = maxOf(word.u0 - u, 0f, u - word.u1)
                val dy = maxOf(word.v0 - v, 0f, v - word.v1)
                word to (dx * dx + dy * dy)
            }
            .filter { it.second <= tolerance * tolerance }
            .minByOrNull { it.second }
            ?.first
    }
}
