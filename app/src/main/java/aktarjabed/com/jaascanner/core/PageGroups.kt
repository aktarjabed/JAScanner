package aktarjabed.com.jaascanner.core

sealed interface PageGroupsResult {
    /** Each inner list is one output file: zero-based, sorted, de-duplicated page indices. */
    data class Ok(val groups: List<List<Int>>) : PageGroupsResult

    data class Error(val message: String) : PageGroupsResult
}

/**
 * Parses "split into ranges" input. Groups are separated by semicolons and each group uses the
 * same syntax as [PageSelection], for example "1-3; 4-9; 10". Pure Kotlin, so it is unit-tested.
 */
object PageGroups {

    fun parse(text: String, pageCount: Int): PageGroupsResult {
        val chunks = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return PageGroupsResult.Error("Enter at least one range, for example 1-3; 4-6.")
        val groups = ArrayList<List<Int>>(chunks.size)
        for (chunk in chunks) {
            when (val parsed = PageSelection.parse(chunk, pageCount)) {
                is PageSelectionResult.Ok -> groups.add(parsed.pages)
                is PageSelectionResult.Error -> return PageGroupsResult.Error(parsed.message)
            }
        }
        return PageGroupsResult.Ok(groups)
    }
}

/** Parses a full new page order such as "3, 1, 2". Every page must appear exactly once. */
object PageOrder {

    fun parse(text: String, pageCount: Int): PageSelectionResult {
        val tokens = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size != pageCount) {
            return PageSelectionResult.Error("List all $pageCount pages once each, for example ${(1..pageCount).joinToString(",")}.")
        }
        val order = ArrayList<Int>(pageCount)
        for (token in tokens) {
            val page = token.toIntOrNull() ?: return PageSelectionResult.Error("\"$token\" is not a page number.")
            if (page < 1 || page > pageCount) return PageSelectionResult.Error("Page $page is outside 1-$pageCount.")
            if (page - 1 in order) return PageSelectionResult.Error("Page $page is listed more than once.")
            order.add(page - 1)
        }
        return PageSelectionResult.Ok(order)
    }
}
