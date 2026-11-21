package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.FontGuess
import aktarjabed.com.jaascanner.core.FontKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Picks a font to draw replacement or recognised text with.
 *
 * The 14 standard PDF fonts are tried first for Latin text. Anything else is looked up in the
 * phone's actual font files: the list is discovered at runtime instead of assuming a few hard-coded
 * ROM filenames, so firmware updates and different vendors still work. A font is only accepted
 * after PDFBox confirms it can encode every character of the text.
 */
object SystemFonts {

    private val FONT_DIRS = listOf(
        "/system/fonts",
        "/system/font",
        "/vendor/fonts",
        "/product/fonts",
        "/apex/com.android.fonts/fonts",
    )
    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    private const val MAX_CANDIDATES = 64

    /** Font files are fixed for the lifetime of the process, so discover them only once. */
    private val discovered: List<File> by lazy {
        val seen = HashSet<String>()
        FONT_DIRS.asSequence()
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().maxDepth(2) }
            .filter { file ->
                file.isFile && file.canRead() &&
                    file.extension.lowercase() in FONT_EXTENSIONS &&
                    seen.add(file.path)
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun pick(document: PDDocument, guess: FontGuess, text: String, cache: HashMap<String, PDFont>): PDFont? {
        val script = scriptOf(text)
        // A PDFont belongs to the document that loaded it. Including the document identity keeps a
        // cached font from being reused in a later document (for example a second searchable copy).
        val key = "${System.identityHashCode(document)}|${guess.kind}|${guess.bold}|${guess.italic}|$script"
        cache[key]?.let { cached ->
            if (canDraw(cached, text)) return cached
            cache.remove(key)
        }

        val standard = standard(guess)
        if (canDraw(standard, text)) return standard

        // Devanagari text often includes the rupee sign; try Devanagari fonts first, then the
        // general symbol fonts if no Devanagari font on this ROM covers the glyph.
        val scripts = if (script == "devanagari") listOf("devanagari", "other") else listOf(script)
        for (fontFile in scripts.asSequence().flatMap { candidates(guess, it).asSequence() }.distinct()) {
            val font = runCatching { PDType0Font.load(document, fontFile) }.getOrNull() ?: continue
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
        text.any { it.code in 0x0900..0x097F || it == '₹' } -> "devanagari"
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

    /** Best-matching font files first, based on the detected script and the requested style. */
    private fun candidates(guess: FontGuess, script: String): List<File> =
        discovered
            .filter { file -> scriptMatches(file.name, script) }
            .sortedWith(
                compareByDescending<File> { familyRank(it.name, guess.kind, script) }
                    .thenByDescending { styleRank(it.name, guess.bold, guess.italic) }
                    .thenBy { it.name },
            )
            .take(MAX_CANDIDATES)

    private fun scriptMatches(name: String, script: String): Boolean {
        val lower = name.lowercase()
        return when (script) {
            "devanagari" -> "devanagari" in lower || "hindi" in lower
            "other" -> listOf("noto", "symbol", "fallback", "droid", "arialuni").any { it in lower }
            else -> true
        }
    }

    private fun familyRank(name: String, kind: FontKind, script: String): Int {
        val lower = name.lowercase()
        return when (script) {
            "devanagari" -> when {
                "devanagariui" in lower -> 3
                "devanagari" in lower -> 2
                "hindi" in lower -> 1
                else -> 0
            }
            "other" -> when {
                "noto" in lower -> 3
                "symbol" in lower -> 2
                "fallback" in lower || "arialuni" in lower -> 1
                else -> 0
            }
            else -> when (kind) {
                FontKind.Serif -> when {
                    "serif" in lower -> 3
                    "times" in lower -> 2
                    else -> 0
                }
                FontKind.Mono -> when {
                    "mono" in lower -> 3
                    "courier" in lower -> 2
                    else -> 0
                }
                FontKind.Sans -> when {
                    "sans" in lower -> 3
                    "roboto" in lower -> 2
                    "droid" in lower -> 1
                    else -> 0
                }
                FontKind.Script -> when {
                    "script" in lower || "hand" in lower -> 3
                    "serif" in lower -> 1
                    else -> 0
                }
            }
        }
    }

    private fun styleRank(name: String, bold: Boolean, italic: Boolean): Int {
        val lower = name.lowercase()
        val hasBold = listOf("bold", "black", "heavy", "semibold").any { it in lower }
        val hasItalic = "italic" in lower || "oblique" in lower
        return when {
            bold && italic -> if (hasBold && hasItalic) 4 else if (hasBold || hasItalic) 2 else 0
            bold -> when {
                hasBold && !hasItalic -> 4
                hasBold -> 3
                else -> 0
            }
            italic -> when {
                hasItalic && !hasBold -> 4
                hasItalic -> 3
                else -> 0
            }
            else -> when {
                !hasBold && !hasItalic -> 4
                "regular" in lower -> 3
                else -> 1
            }
        }
    }
}
