package aktarjabed.com.jaascanner.core

enum class FontKind(val label: String) {
    Sans("Sans-serif"),
    Serif("Serif"),
    Mono("Monospaced"),
    Script("Handwriting / script"),
}

data class FontGuess(val kind: FontKind, val bold: Boolean, val italic: Boolean, val cleanName: String) {
    val label: String
        get() = buildString {
            append(kind.label)
            if (bold) append(", bold")
            if (italic) append(", italic")
        }
}

/** Works out what a PDF font looks like from its base font name (for example "ABCDEF+Arial-BoldMT"). */
object FontGuessing {

    private val SUBSET_PREFIX = Regex("^[A-Z]{6}\\+")
    private val BOLD = Regex("bold|black|heavy|demi|semibold|-bd(?![a-z])|\\.bd(?![a-z])")
    private val ITALIC = Regex("italic|oblique|slanted|-it(?![a-z])|-ital(?![a-z])")
    private val SCRIPT = listOf("script", "handwrit", "brush", "cursive", "chancery", "comic", "marker", "dancing", "signature", "calligra", "pacifico")
    private val MONO = listOf("courier", "mono", "consolas", "typewriter", "menlo", "inconsolata", "lucidaconsole", "cutive")
    private val SANS = listOf("sans", "arial", "helvet", "calibri", "verdana", "tahoma", "roboto", "segoe", "gothic", "myriad", "opensans", "lato")
    private val SERIF = listOf("times", "serif", "georgia", "garamond", "palatino", "cambria", "minion", "bookman", "century", "baskerville", "caslon", "didot", "roman", "mincho", "cmr", "cmbx", "charter")

    /** True when the name carries the six-capital-letter prefix PDF producers add to subset fonts. */
    fun isSubset(name: String?): Boolean = name != null && SUBSET_PREFIX.containsMatchIn(name)

    fun guess(baseName: String?): FontGuess {
        val clean = (baseName ?: "").replace(SUBSET_PREFIX, "")
        val lower = clean.lowercase().replace(" ", "")
        val kind = when {
            SCRIPT.any { it in lower } -> FontKind.Script
            MONO.any { it in lower } -> FontKind.Mono
            SANS.any { it in lower } -> FontKind.Sans
            SERIF.any { it in lower } -> FontKind.Serif
            else -> FontKind.Sans
        }
        return FontGuess(
            kind = kind,
            bold = BOLD.containsMatchIn(lower),
            italic = ITALIC.containsMatchIn(lower),
            cleanName = clean.ifBlank { "unknown font" },
        )
    }
}
