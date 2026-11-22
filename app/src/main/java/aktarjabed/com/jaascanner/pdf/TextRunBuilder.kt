package aktarjabed.com.jaascanner.pdf

import kotlin.math.abs

/**
 * One glyph exactly as the PDF engine reports it after page rotation has been applied.
 *
 * [x] and [y] are top-left-origin coordinates on the page as displayed, [advance] is the width
 * along the displayed baseline, and [textDirection] is the direction the text runs in the
 * unrotated page (0, 90, 180 or 270 degrees). Keeping this type free of PDFBox lets the whole
 * grouping and box calculation run in plain JVM unit tests.
 */
data class GlyphSample(
    val unicode: String,
    val x: Float,
    val y: Float,
    val advance: Float,
    val size: Float,
    val textDirection: Int,
    val fontName: String,
)

/**
 * One word of real (selectable) text on a page.
 * Boxes are fractions of the page as displayed: u to the right, v downwards.
 * Only text that is upright on the displayed page becomes a [TextWord].
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

/**
 * Groups page-rotation-adjusted glyphs into upright, editable words.
 *
 * Only text that reads left-to-right and upright on the displayed page is turned into words:
 * for a page rotated by R and text running in direction D in the page, the text is upright on
 * screen when `(R - D) mod 360 == 0`. This covers every normal rotated PDF: `/Rotate 90` with
 * text drawn at 90 degrees, `/Rotate 180` with text drawn at 180 degrees, and so on. Text that
 * appears sideways or upside down on screen is still appended to [PageText.flat] (so in-place
 * replacement can find it) but is not offered as an editable word.
 */
object TextRunBuilder {

    private const val ASCENT = 0.80f
    private const val DESCENT = 0.22f
    private const val GAP_FRACTION = 0.28f

    /** Wraps a direction to 0, 90, 180 or 270 degrees. */
    fun normalizeDirection(degrees: Int): Int {
        val wrapped = ((degrees % 360) + 360) % 360
        return (wrapped / 90) * 90
    }

    /** Direction the text runs on the displayed page; 0 means upright and left-to-right. */
    fun displayDirection(pageRotation: Int, textDirection: Int): Int =
        normalizeDirection(pageRotation - textDirection)

    fun isUprightOnScreen(pageRotation: Int, textDirection: Int): Boolean =
        displayDirection(pageRotation, textDirection) == 0

    fun build(
        glyphs: List<GlyphSample>,
        pageIndex: Int,
        pageRotation: Int,
        displayedWidth: Float,
        displayedHeight: Float,
    ): PageText {
        val flat = StringBuilder()
        val words = ArrayList<TextWord>()
        var current = ArrayList<GlyphSample>()
        var currentStart = 0
        var previous: GlyphSample? = null

        fun flush() {
            if (current.isEmpty()) return
            val text = current.joinToString("") { it.unicode }
            if (text.isNotBlank() && displayedWidth > 0f && displayedHeight > 0f) {
                val left = current.minOf { it.x }
                val right = current.maxOf { it.x + it.advance }
                val baseline = current.first().y
                val top = current.minOf { it.y - it.size * ASCENT }
                val bottom = current.maxOf { it.y + it.size * DESCENT }
                val size = current.maxOf { it.size }.coerceAtLeast(1f)
                words.add(
                    TextWord(
                        text = text,
                        page = pageIndex,
                        u0 = (left / displayedWidth).coerceIn(0f, 1f),
                        v0 = (top / displayedHeight).coerceIn(0f, 1f),
                        u1 = (right / displayedWidth).coerceIn(0f, 1f),
                        v1 = (bottom / displayedHeight).coerceIn(0f, 1f),
                        baselineV = (baseline / displayedHeight).coerceIn(0f, 1f),
                        fontSizeFrac = size / displayedWidth,
                        fontName = current.first().fontName,
                        flatStart = currentStart,
                        flatEnd = currentStart + text.length,
                    ),
                )
            }
            current = ArrayList()
        }

        for (glyph in glyphs) {
            val unicode = glyph.unicode
            val upright = unicode.isNotBlank() && isUprightOnScreen(pageRotation, glyph.textDirection)
            if (!upright) {
                flush()
                flat.append(unicode)
                previous = null
                continue
            }

            val before = previous
            val sameLine = before != null &&
                abs(glyph.y - before.y) <= maxOf(glyph.size, before.size) * 0.5f
            val limit = if (before == null) glyph.size else maxOf(glyph.size, before.size)
            val gap = if (before == null) 0f else glyph.x - (before.x + before.advance)
            val startsNewWord = before == null || !sameLine ||
                (glyph.advance > 0f && (gap > limit * GAP_FRACTION || gap < -limit))
            // A zero-width glyph (combining mark) only joins a word when it sits on the same
            // line; otherwise it belongs to no word and is only kept in the flat string.
            val attach = glyph.advance > 0f || sameLine

            if (startsNewWord) flush()
            if (attach) {
                if (current.isEmpty()) currentStart = flat.length
                current.add(glyph)
                if (glyph.advance > 0f) previous = glyph
            } else {
                flush()
                previous = null
            }
            flat.append(unicode)
        }
        flush()
        return PageText(words, flat.toString())
    }
}
