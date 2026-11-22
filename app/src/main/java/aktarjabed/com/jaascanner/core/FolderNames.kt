package aktarjabed.com.jaascanner.core

/**
 * Cleans user-entered folder names before they reach the database.
 *
 * Pure Kotlin so the rules are unit-tested. The same rules apply to new folders and renames;
 * otherwise a name accepted at creation could become impossible to rename back to later.
 */
object FolderNames {

    /** Long enough for a descriptive name, short enough to stay readable in chips and pickers. */
    const val MAX_CODE_POINTS = 80

    /**
     * Collapses whitespace and control characters, trims, and caps the length by code point.
     * Returns null for a name that has no visible content (blank, only controls, "." or "..").
     */
    fun clean(raw: String): String? {
        val flattened = buildString(raw.length) {
            raw.forEach { character ->
                append(if (character.isISOControl()) ' ' else character)
            }
        }
        val collapsed = flattened.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return null
        val capped = Formatters.truncateCodePoints(collapsed, MAX_CODE_POINTS).trim()
        if (capped.isEmpty() || capped == "." || capped == "..") return null
        return capped
    }
}
