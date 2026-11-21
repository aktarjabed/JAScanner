package aktarjabed.com.jaascanner.core

/**
 * One page in the page organiser. [id] never changes while the user rearranges (so a dragged page
 * can be followed); [source] is the zero-based page of the original PDF; [rotation] is the extra
 * clockwise turn in degrees (a multiple of 90) applied on save.
 */
data class PageEntry(val id: Int, val source: Int, val rotation: Int = 0)

/** Pure list operations behind the page organiser (drag to reorder, rotate, delete, duplicate). */
object PageList {

    fun initial(count: Int): List<PageEntry> = List(count) { PageEntry(id = it, source = it) }

    /** Moves the page at [from] so it ends up at index [to] (both clamped into range). */
    fun move(list: List<PageEntry>, from: Int, to: Int): List<PageEntry> {
        if (from !in list.indices) return list
        val target = to.coerceIn(0, list.lastIndex)
        if (target == from) return list
        val result = list.toMutableList()
        result.add(target, result.removeAt(from))
        return result
    }

    /** Moves the chosen pages one place earlier (-1) or later (+1), keeping their order among themselves. */
    fun shift(list: List<PageEntry>, ids: Set<Int>, delta: Int): List<PageEntry> {
        if (ids.isEmpty() || delta == 0) return list
        val result = list.toMutableList()
        if (delta < 0) {
            for (index in 1..result.lastIndex) {
                if (result[index].id in ids && result[index - 1].id !in ids) {
                    val moved = result.removeAt(index)
                    result.add(index - 1, moved)
                }
            }
        } else {
            for (index in result.lastIndex - 1 downTo 0) {
                if (result[index].id in ids && result[index + 1].id !in ids) {
                    val moved = result.removeAt(index)
                    result.add(index + 1, moved)
                }
            }
        }
        return result
    }

    /**
     * Takes the chosen pages out (keeping their order) and puts them back as one block starting at
     * [position] (1-based, counted among the pages that remain, so 1 means "first").
     */
    fun moveTo(list: List<PageEntry>, ids: Set<Int>, position: Int): List<PageEntry> {
        if (ids.isEmpty()) return list
        val chosen = list.filter { it.id in ids }
        val rest = list.filter { it.id !in ids }
        val at = (position - 1).coerceIn(0, rest.size)
        return rest.take(at) + chosen + rest.drop(at)
    }

    fun rotate(list: List<PageEntry>, ids: Set<Int>, degrees: Int): List<PageEntry> =
        list.map { if (it.id in ids) it.copy(rotation = PdfCoordinateMapper.normalizeRotation(it.rotation + degrees)) else it }

    /** Removes the chosen pages, but never all of them: a document keeps at least one page. */
    fun delete(list: List<PageEntry>, ids: Set<Int>): List<PageEntry> {
        val rest = list.filter { it.id !in ids }
        return if (rest.isEmpty()) list else rest
    }

    /** Puts a copy of each chosen page right after it. New ids start at [firstNewId]. */
    fun duplicate(list: List<PageEntry>, ids: Set<Int>, firstNewId: Int): List<PageEntry> {
        var next = firstNewId
        val result = ArrayList<PageEntry>(list.size + ids.size)
        for (entry in list) {
            result.add(entry)
            if (entry.id in ids) result.add(entry.copy(id = next++))
        }
        return result
    }

    fun nextId(list: List<PageEntry>): Int = (list.maxOfOrNull { it.id } ?: -1) + 1

    /** True when the list is exactly the original pages in the original order with no turns. */
    fun isUnchanged(list: List<PageEntry>, originalCount: Int): Boolean =
        list.size == originalCount && list.withIndex().all { (index, entry) -> entry.source == index && entry.rotation == 0 }

    /** Index in [list] of the entry whose [PageEntry.id] is [id], or -1. */
    fun indexOfId(list: List<PageEntry>, id: Int): Int = list.indexOfFirst { it.id == id }
}
