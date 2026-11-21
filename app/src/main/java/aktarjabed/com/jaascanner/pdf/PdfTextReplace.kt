package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.FontGuessing
import aktarjabed.com.jaascanner.core.TextDiff
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs

/**
 * Edits a word by changing the characters inside the page's own text-drawing commands.
 * Because the original font draws the new letters, a one-letter change looks exactly like the
 * original. It works only when the font already contains every new letter (the letter appears
 * elsewhere on the page in the same font, or the font is not a subset) and the page is a plain
 * content stream; otherwise it reports why not and the caller uses a look-alike overlay.
 */
object PdfTextReplace {

    sealed interface Outcome {
        data object Done : Outcome

        data class Unsupported(val reason: String) : Outcome
    }

    /** Dry run on a copy in memory: tells whether this edit can use the original font. */
    fun check(file: File, word: TextWord, newText: String): Outcome =
        PDDocument.load(file).use { replaceInPlace(it, word, newText) }

    private class Code(val bytes: ByteArray, val unicode: String, val width: Float)

    private class Element(
        val operandIndex: Int,
        val arrayIndex: Int,
        val operatorIndex: Int,
        val font: PDFont,
        val codes: List<Code>,
        val flatStart: Int,
    ) {
        val flatEnd: Int = flatStart + codes.sumOf { it.unicode.length }
    }

    fun replaceInPlace(document: PDDocument, original: TextWord, newText: String): Outcome {
        val pageIndex = original.page
        if (pageIndex !in 0 until document.numberOfPages) return Outcome.Unsupported("page not found")
        val page = document.getPage(pageIndex)
        val resources = page.resources ?: return Outcome.Unsupported("page has no resources")

        // Re-read the page as it is now, so earlier edits on the same page are respected.
        val now = PdfTextRuns.analyse(document, pageIndex)
        val word = now.words
            .filter { it.text == original.text }
            .minByOrNull { abs(it.u0 - original.u0) + abs(it.v0 - original.v0) }
            ?: return Outcome.Unsupported("the word is no longer on the page")
        val change = TextDiff.change(word.text, newText) ?: return Outcome.Done

        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens: MutableList<Any> = parser.tokens.toMutableList()

        // Pass 1: every shown string, with its font and decoded characters.
        val elements = ArrayList<Element>()
        var font: PDFont? = null
        val operands = ArrayList<Int>()
        var flatLength = 0
        for ((index, token) in tokens.withIndex()) {
            if (token !is Operator) {
                operands.add(index)
                continue
            }
            when (token.name) {
                "Tf" -> {
                    val name = operands.firstOrNull()?.let { tokens[it] as? COSName }
                    font = name?.let { runCatching { resources.getFont(it) }.getOrNull() }
                }
                "Tj", "'", "\"" -> {
                    val at = operands.lastOrNull()
                    val string = at?.let { tokens[it] as? COSString }
                    val f = font
                    if (string != null && f != null) {
                        val element = element(at, -1, index, f, string.bytes, flatLength)
                        elements.add(element)
                        flatLength = element.flatEnd
                    }
                }
                "TJ" -> {
                    val at = operands.lastOrNull()
                    val array = at?.let { tokens[it] as? COSArray }
                    val f = font
                    if (array != null && f != null) {
                        for (i in 0 until array.size()) {
                            val string = array.get(i) as? COSString ?: continue
                            val element = element(at, i, index, f, string.bytes, flatLength)
                            elements.add(element)
                            flatLength = element.flatEnd
                        }
                    }
                }
            }
            operands.clear()
        }
        val streamFlat = elements.joinToString("") { e -> e.codes.joinToString("") { it.unicode } }
        if (streamFlat != now.flat) {
            return Outcome.Unsupported("this page draws text in a way that cannot be edited safely in place")
        }

        // Locate the changed characters inside one drawn string.
        val a = word.flatStart + change.start
        val b = word.flatStart + change.oldEnd
        val target = elements.firstOrNull { it.flatStart <= a && b <= it.flatEnd && (a < it.flatEnd || a == b) }
            ?: return Outcome.Unsupported("the word is split across several text commands")
        if (target.font.isVertical) return Outcome.Unsupported("vertical text")
        val first = boundary(target, a) ?: return Outcome.Unsupported("edit does not line up with the font's characters")
        val last = boundary(target, b) ?: return Outcome.Unsupported("edit does not line up with the font's characters")

        // Letters the font can draw: those already used with this font on the page, or any letter
        // of a font that is not a subset.
        val known = HashMap<String, Code>()
        for (e in elements) {
            if (e.font.cosObject === target.font.cosObject) for (c in e.codes) known.putIfAbsent(c.unicode, c)
        }
        val inserted = ArrayList<Code>()
        val replacement = newText.substring(change.start, change.newEnd)
        var cursor = 0
        while (cursor < replacement.length) {
            val cp = replacement.codePointAt(cursor)
            val letter = String(Character.toChars(cp))
            cursor += letter.length
            val code = known[letter]
                ?: fromFont(target.font, letter)
                ?: return Outcome.Unsupported("the original font has no \"$letter\"")
            inserted.add(code)
        }

        fun join(codes: List<Code>): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            for (c in codes) out.write(c.bytes)
            return out.toByteArray()
        }
        val before = join(target.codes.subList(0, first) + inserted)
        val after = join(target.codes.subList(last, target.codes.size))

        // The rest of the line follows naturally: later letters of the same text command move by the
        // width difference, exactly as in a word processor. Separately placed text stays where it is.
        if (target.arrayIndex < 0) {
            tokens[target.operandIndex] = COSString(before + after)
        } else {
            val array = tokens[target.operandIndex] as COSArray
            val rebuilt = ArrayList<COSBase>()
            for (i in 0 until array.size()) {
                if (i != target.arrayIndex) rebuilt.add(array.get(i)) else rebuilt.add(COSString(before + after))
            }
            array.clear()
            for (item in rebuilt) array.add(item)
        }

        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(stream)
        return Outcome.Done
    }

    private fun element(
        operandIndex: Int,
        arrayIndex: Int,
        operatorIndex: Int,
        font: PDFont,
        data: ByteArray,
        flatStart: Int,
    ): Element = Element(operandIndex, arrayIndex, operatorIndex, font, decode(font, data), flatStart)

    /** Index of the code that starts at flat offset [flat] inside [element]; null if it falls inside a code. */
    private fun boundary(element: Element, flat: Int): Int? {
        var at = element.flatStart
        for ((i, code) in element.codes.withIndex()) {
            if (at == flat) return i
            at += code.unicode.length
        }
        return if (at == flat) element.codes.size else null
    }

    private fun decode(font: PDFont, data: ByteArray): List<Code> {
        val input = ByteArrayInputStream(data)
        val out = ArrayList<Code>()
        while (input.available() > 0) {
            val start = data.size - input.available()
            val code = font.readCode(input)
            val end = data.size - input.available()
            val unicode = runCatching { font.toUnicode(code) }.getOrNull()
                ?: if (font is PDSimpleFont) code.toChar().toString() else ""
            val width = runCatching { font.getWidth(code) }.getOrDefault(0f)
            out.add(Code(data.copyOfRange(start, end), unicode, width))
        }
        return out
    }

    /** Asks a font that is not a subset to encode a letter; subset fonts are never trusted. */
    private fun fromFont(font: PDFont, letter: String): Code? {
        if (FontGuessing.isSubset(font.name)) return null
        val bytes = runCatching { font.encode(letter) }.getOrNull() ?: return null
        val codes = runCatching { decode(font, bytes) }.getOrNull() ?: return null
        return codes.singleOrNull()?.takeIf { it.unicode == letter }
    }
}
