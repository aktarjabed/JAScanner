package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.FontGuess
import aktarjabed.com.jaascanner.core.FontKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Picks a font to draw replacement text with: one of the 14 standard PDF fonts when it can draw the
 * text, otherwise one of the phone's own font files (which also covers accented letters).
 */
object SystemFonts {

    private const val DIR = "/system/fonts/"

    fun pick(document: PDDocument, guess: FontGuess, text: String, cache: HashMap<String, PDFont>): PDFont? {
        val key = "${guess.kind}|${guess.bold}|${guess.italic}|${scriptOf(text)}"
        cache[key]?.let { if (canDraw(it, text)) return it }

        val standard = standard(guess)
        if (canDraw(standard, text)) return standard

        for (name in candidates(guess, text)) {
            val file = File(DIR + name)
            if (!file.canRead()) continue
            val font = runCatching { PDType0Font.load(document, file) }.getOrNull() ?: continue
            if (canDraw(font, text)) {
                cache[key] = font
                return font
            }
        }
        return null
    }

    private fun canDraw(font: PDFont, text: String): Boolean =
        runCatching { font.encode(text) }.isSuccess

    private fun scriptOf(text: String): String = when {
        text.any { it.code in 0x0900..0x097F } -> "devanagari"
        text.any { it.code > 0x24F } -> "other"
        else -> "latin"
    }

    private fun standard(guess: FontGuess): PDFont = when (guess.kind) {
        FontKind.Serif -> when {
            guess.bold && guess.italic -> PDType1Font.TIMES_BOLD_ITALIC
            guess.bold -> PDType1Font.TIMES_BOLD
            guess.italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        FontKind.Mono -> when {
            guess.bold && guess.italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            guess.bold -> PDType1Font.COURIER_BOLD
            guess.italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
        else -> when {
            guess.bold && guess.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            guess.bold -> PDType1Font.HELVETICA_BOLD
            guess.italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }

    private fun candidates(guess: FontGuess, text: String): List<String> {
        if (scriptOf(text) == "devanagari") {
            return listOf(
                if (guess.bold) "NotoSansDevanagari-Bold.ttf" else "NotoSansDevanagari-Regular.ttf",
                "NotoSansDevanagari-Regular.ttf",
                "NotoSansDevanagariUI-Regular.ttf",
            )
        }
        val style = when {
            guess.bold && guess.italic -> "BoldItalic"
            guess.bold -> "Bold"
            guess.italic -> "Italic"
            else -> "Regular"
        }
        return when (guess.kind) {
            FontKind.Serif -> listOf("NotoSerif-$style.ttf", "NotoSerif-Regular.ttf", "Roboto-Regular.ttf")
            FontKind.Mono -> listOf("DroidSansMono.ttf", "CutiveMono.ttf", "Roboto-Regular.ttf")
            else -> listOf("Roboto-$style.ttf", "Roboto-Regular.ttf", "NotoSans-Regular.ttf")
        }
    }
}
