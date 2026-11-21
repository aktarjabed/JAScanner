package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.FontGuessing
import aktarjabed.com.jaascanner.core.TextDiff
import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Edits a word by changing the characters inside the page's own text-drawing commands.
 * Because the original font draws the new letters, a one-letter change looks exactly like the
 * original. It works only when the font already contains every new letter (the letter appears
 * elsewhere in the document in the same font, or the font is not a subset). Text drawn inside Form
 * XObjects is parsed recursively; anything more unusual is reported so the caller can use a
 * look-alike overlay instead.
 */
object PdfTextReplace {

    sealed interface Outcome {
        data object Done : Outcome

        data class Unsupported(val reason: String) : Outcome
    }

    private const val MAX_XOBJECT_DEPTH = 16

    private class UnsupportedEdit(message: String) : Exception(message)

    /** A page content stream or a Form XObject stream whose tokens may be rewritten. */
    private sealed class ContentTarget {
        abstract var tokens: MutableList<Any>
    }

    private class PageTarget(val page: PDPage) : ContentTarget() {
        override var tokens: MutableList<Any> = mutableListOf()
    }

    private class FormTarget(val form: PDFormXObject) : ContentTarget() {
        override var tokens: MutableList<Any> = mutableListOf()
    }

    private class Code(val bytes: ByteArray, val unicode: String, val width: Float)

    private class Element(
        val target: ContentTarget,
        val operandIndex: Int,
        val arrayIndex: Int,
        val font: PDFont,
        val codes: List<Code>,
        val flatStart: Int,
    ) {
        val flatEnd: Int = flatStart + codes.sumOf { it.unicode.length }
    }

    /** Dry run on a copy in memory: tells whether this edit can use the original font. */
    fun check(file: File, word: TextWord, newText: String): Outcome =
        PDDocument.load(file).use { replaceInPlace(it, word, newText) }

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

        val elements = ArrayList<Element>()
        val pageTarget = PageTarget(page)
        try {
            parseStream(
                stream = page,
                resources = resources,
                inheritedFont = null,
                target = pageTarget,
                elements = elements,
                formUses = IdentityHashMap(),
                ancestors = Collections.newSetFromMap(IdentityHashMap<COSStream, Boolean>()),
                depth = 0,
                startFlat = 0,
            )
        } catch (error: UnsupportedEdit) {
            return Outcome.Unsupported(error.message ?: "could not analyse the page")
        } catch (error: Exception) {
            return Outcome.Unsupported("could not analyse the page")
        }

        val streamFlat = elements.joinToString("") { element -> element.codes.joinToString("") { it.unicode } }
        if (streamFlat != now.flat) {
            return Outcome.Unsupported("this page draws text in a way that cannot be edited safely in place")
        }

        // Locate the changed characters inside one drawn string.
        val a = word.flatStart + change.start
        val b = word.flatStart + change.oldEnd
        val hit = elements.firstOrNull { it.flatStart <= a && b <= it.flatEnd && (a < it.flatEnd || a == b) }
            ?: return Outcome.Unsupported("the word is split across several text commands")
        if (hit.font.isVertical) return Outcome.Unsupported("vertical text")
        val first = boundary(hit, a) ?: return Outcome.Unsupported("edit does not line up with the font's characters")
        val last = boundary(hit, b) ?: return Outcome.Unsupported("edit does not line up with the font's characters")

        // Letters the font can draw: those already used with this font in the parsed streams, or
        // any letter of a font that is not a subset.
        val known = HashMap<String, Code>()
        for (element in elements) {
            if (element.font.cosObject === hit.font.cosObject) {
                for (code in element.codes) known.putIfAbsent(code.unicode, code)
            }
        }
        val inserted = ArrayList<Code>()
        val replacement = newText.substring(change.start, change.newEnd)
        var cursor = 0
        while (cursor < replacement.length) {
            val cp = replacement.codePointAt(cursor)
            val letter = String(Character.toChars(cp))
            cursor += letter.length
            val code = known[letter]
                ?: fromFont(hit.font, letter)
                ?: return Outcome.Unsupported("the original font has no \"$letter\"")
            inserted.add(code)
        }

        fun join(codes: List<Code>): ByteArray {
            val out = ByteArrayOutputStream()
            for (code in codes) out.write(code.bytes)
            return out.toByteArray()
        }
        val before = join(hit.codes.subList(0, first) + inserted)
        val after = join(hit.codes.subList(last, hit.codes.size))

        // The rest of the line follows naturally: later letters of the same text command move by the
        // width difference, exactly as in a word processor. Separately placed text stays where it is.
        val tokens = hit.target.tokens
        if (hit.arrayIndex < 0) {
            tokens[hit.operandIndex] = COSString(before + after)
        } else {
            val array = tokens[hit.operandIndex] as COSArray
            val rebuilt = ArrayList<COSBase>()
            for (i in 0 until array.size()) {
                if (i != hit.arrayIndex) rebuilt.add(array.get(i)) else rebuilt.add(COSString(before + after))
            }
            array.clear()
            for (item in rebuilt) array.add(item)
        }

        writeBack(document, hit.target)
        return Outcome.Done
    }

    /**
     * Walks a content stream in drawing order. A Form XObject is expanded where its `Do` operator
     * appears, with its own resources when present and the caller's graphics state inherited.
     * Returns the running flat-text offset after this stream.
     */
    private fun parseStream(
        stream: PDContentStream,
        resources: PDResources,
        inheritedFont: PDFont?,
        target: ContentTarget,
        elements: MutableList<Element>,
        formUses: MutableMap<COSStream, Int>,
        ancestors: MutableSet<COSStream>,
        depth: Int,
        startFlat: Int,
    ): Int {
        if (depth > MAX_XOBJECT_DEPTH) throw UnsupportedEdit("form nesting is too deep")
        val parser = PDFStreamParser(stream)
        parser.parse()
        val tokens = parser.tokens.toMutableList()
        target.tokens = tokens

        var font = inheritedFont
        val operands = ArrayList<Int>()
        var flatLength = startFlat
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
                    val currentFont = font
                    if (string != null && currentFont != null) {
                        val parsed = element(target, at, -1, currentFont, string.bytes, flatLength)
                        elements.add(parsed)
                        flatLength = parsed.flatEnd
                    }
                }
                "TJ" -> {
                    val at = operands.lastOrNull()
                    val array = at?.let { tokens[it] as? COSArray }
                    val currentFont = font
                    if (array != null && currentFont != null) {
                        for (i in 0 until array.size()) {
                            val string = array.get(i) as? COSString ?: continue
                            val parsed = element(target, at, i, currentFont, string.bytes, flatLength)
                            elements.add(parsed)
                            flatLength = parsed.flatEnd
                        }
                    }
                }
                "Do" -> {
                    val name = operands.lastOrNull()?.let { tokens[it] as? COSName }
                    val xobject = name?.let { runCatching { resources.getXObject(it) }.getOrNull() }
                    if (xobject is PDFormXObject) {
                        val formStream = xobject.cosObject
                        if (!ancestors.add(formStream)) throw UnsupportedEdit("recursive form XObject")
                        val uses = (formUses[formStream] ?: 0) + 1
                        formUses[formStream] = uses
                        if (uses > 1) throw UnsupportedEdit("a form is drawn more than once")
                        val formTarget = FormTarget(xobject)
                        flatLength = parseStream(
                            stream = xobject,
                            resources = xobject.resources ?: resources,
                            inheritedFont = font,
                            target = formTarget,
                            elements = elements,
                            formUses = formUses,
                            ancestors = ancestors,
                            depth = depth + 1,
                            startFlat = flatLength,
                        )
                        ancestors.remove(formStream)
                    }
                }
            }
            operands.clear()
        }
        return flatLength
    }

    private fun writeBack(document: PDDocument, target: ContentTarget) {
        when (target) {
            is PageTarget -> {
                val stream = PDStream(document)
                stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(target.tokens) }
                target.page.setContents(stream)
            }
            is FormTarget -> {
                target.form.cosObject.createOutputStream(COSName.FLATE_DECODE).use {
                    ContentStreamWriter(it).writeTokens(target.tokens)
                }
            }
        }
    }

    private fun element(
        target: ContentTarget,
        operandIndex: Int,
        arrayIndex: Int,
        font: PDFont,
        data: ByteArray,
        flatStart: Int,
    ): Element = Element(target, operandIndex, arrayIndex, font, decode(font, data), flatStart)

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
