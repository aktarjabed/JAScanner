package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

object PdfTextRuns {

    private class Collector : PDFTextStripper() {
        val glyphs = ArrayList<TextPosition>()

        override fun processTextPosition(text: TextPosition) {
            glyphs.add(text)
        }
    }

    fun analyse(document: PDDocument, pageIndex: Int): PageText {
        val page = document.getPage(pageIndex)
        val crop = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val turned = rotation % 180 != 0
        val width = if (turned) crop.height else crop.width
        val height = if (turned) crop.width else crop.height

        val collector = Collector()
        collector.startPage = pageIndex + 1
        collector.endPage = pageIndex + 1
        collector.getText(document)

        // TextPosition.getX()/getY() are page-rotation adjusted (top-left origin, y down), and
        // getWidth() is the advance along the displayed baseline. That is exactly the space the
        // viewer draws in, so no unrotated xDirAdj/yDirAdj maths is needed here.
        val glyphs = collector.glyphs.map { glyph ->
            GlyphSample(
                unicode = glyph.unicode ?: "",
                x = glyph.x,
                y = glyph.y,
                advance = glyph.width,
                size = glyph.fontSizeInPt.coerceAtLeast(1f),
                textDirection = glyph.dir.toInt(),
                fontName = glyph.font?.name ?: "",
            )
        }
        return TextRunBuilder.build(glyphs, pageIndex, rotation, width, height)
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
