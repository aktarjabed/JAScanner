package aktarjabed.com.jaascanner.core

sealed interface PageSelectionResult {
    /** Zero-based, sorted, de-duplicated page indices. */
    data class Ok(val pages: List<Int>) : PageSelectionResult

    data class Error(val message: String) : PageSelectionResult
}

/** Parses user input such as "1-3, 5, 8-" into page indices. Pure Kotlin, so it is unit-tested. */
object PageSelection {

    fun parse(text: String, pageCount: Int): PageSelectionResult {
        val tokens = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return PageSelectionResult.Error("Enter at least one page.")

        val result = sortedSetOf<Int>()
        for (token in tokens) {
            val parts = token.split('-').map { it.trim() }
            val range: IntRange = when (parts.size) {
                1 -> {
                    val page = parts[0].toIntOrNull() ?: return invalid(token)
                    page..page
                }
                2 -> {
                    val start = parts[0].toIntOrNull() ?: return invalid(token)
                    val end = if (parts[1].isEmpty()) pageCount else (parts[1].toIntOrNull() ?: return invalid(token))
                    if (end < start) return PageSelectionResult.Error("\"$token\" runs backwards.")
                    start..end
                }
                else -> return invalid(token)
            }
            for (page in range) {
                if (page < 1 || page > pageCount) {
                    return PageSelectionResult.Error("Page $page is outside 1-$pageCount.")
                }
                result.add(page - 1)
            }
        }
        return PageSelectionResult.Ok(result.toList())
    }

    private fun invalid(token: String) = PageSelectionResult.Error("\"$token\" is not a valid page or range.")
}
