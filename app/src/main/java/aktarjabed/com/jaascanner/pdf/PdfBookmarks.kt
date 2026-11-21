package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import java.io.File
import java.util.IdentityHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry of a PDF's own bookmark tree. [level] 0 is top level; [page] is zero-based. */
data class Bookmark(val title: String, val page: Int, val level: Int = 0)

/**
 * Reads, adds and removes bookmarks stored inside the PDF itself, so they show up in every reader.
 * Entries that point outside the document (links to other files, web addresses) are not listed.
 * Changing the list rewrites the whole bookmark tree from the list that was read, so colours and
 * open/closed state of other readers' bookmarks are not kept.
 */
object PdfBookmarks {

    const val MAX_ITEMS = 5000
    private const val MAX_DEPTH = 8
    private const val MAX_TITLE = 120

    fun read(document: PDDocument): List<Bookmark> {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        val pages = document.pages
        val result = ArrayList<Bookmark>()
        val seen = IdentityHashMap<COSBase, Boolean>()

        fun walk(node: PDOutlineNode, level: Int) {
            var item: PDOutlineItem? = node.firstChild
            while (item != null && result.size < MAX_ITEMS) {
                if (seen.put(item.cosObject, true) != null) return
                val page = runCatching { item.findDestinationPage(document) }.getOrNull()
                val index = if (page != null) pages.indexOf(page) else -1
                val title = item.title?.trim().orEmpty()
                if (index >= 0 && title.isNotEmpty()) result.add(Bookmark(title.take(MAX_TITLE), index, level))
                if (level < MAX_DEPTH) walk(item, level + 1)
                item = item.nextSibling
            }
        }
        walk(outline, 0)
        return result
    }

    suspend fun read(file: File): List<Bookmark> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { read(it) }
    }

    /** Replaces the bookmark tree of [document] with [bookmarks]. An empty list removes the tree. */
    fun writeTo(document: PDDocument, bookmarks: List<Bookmark>) {
        val usable = bookmarks.filter { it.page in 0 until document.numberOfPages && it.title.isNotBlank() }
        if (usable.isEmpty()) {
            document.documentCatalog.documentOutline = null
            return
        }
        val outline = PDDocumentOutline()
        // stack[k] is the node that owns level k: the outline itself for level 0.
        val stack = ArrayList<PDOutlineNode>()
        stack.add(outline)
        for (bookmark in usable) {
            val level = bookmark.level.coerceIn(0, stack.size - 1)
            while (stack.size > level + 1) stack.removeAt(stack.size - 1)
            val item = PDOutlineItem()
            item.title = bookmark.title.trim().take(MAX_TITLE)
            item.setDestination(document.getPage(bookmark.page))
            stack[level].addLast(item)
            stack.add(item)
        }
        outline.openNode()
        document.documentCatalog.documentOutline = outline
    }

    /** Adds a top-level bookmark for [page] (zero-based) at the end of the list. */
    suspend fun add(source: File, target: File, title: String, page: Int) {
        edit(source, target) { document ->
            if (page !in 0 until document.numberOfPages) {
                throw PdfToolException("Page ${page + 1} does not exist. This document has ${document.numberOfPages} pages.")
            }
            val name = title.trim().ifEmpty { "Page ${page + 1}" }
            writeTo(document, read(document) + Bookmark(name, page, 0))
        }
    }

    /** Removes the bookmark at [index] of the list that [read] returns (its sub-entries move up a level). */
    suspend fun removeAt(source: File, target: File, index: Int) {
        edit(source, target) { document -> removeAt(document, read(document), index) }
    }

    /**
     * Removes [bookmark] by finding it in the document as it is now. Callers hold a bookmark from
     * an earlier [read]; matching by content instead of position means a second quick removal
     * cannot delete the wrong entry after the first one shifted the indices.
     */
    suspend fun remove(source: File, target: File, bookmark: Bookmark) {
        edit(source, target) { document ->
            val current = read(document)
            val index = current.indexOfFirst {
                it.title == bookmark.title && it.page == bookmark.page && it.level == bookmark.level
            }
            if (index < 0) throw PdfToolException("That bookmark no longer exists.")
            removeAt(document, current, index)
        }
    }

    private fun removeAt(document: PDDocument, current: List<Bookmark>, index: Int) {
        if (index !in current.indices) throw PdfToolException("That bookmark no longer exists.")
        val removedLevel = current[index].level
        val kept = ArrayList<Bookmark>(current.size)
        var inside = true
        for ((position, bookmark) in current.withIndex()) {
            when {
                position == index -> Unit
                position > index && inside && bookmark.level > removedLevel ->
                    kept.add(bookmark.copy(level = bookmark.level - 1))
                else -> {
                    if (position > index) inside = false
                    kept.add(bookmark)
                }
            }
        }
        writeTo(document, kept)
    }

    private suspend fun edit(source: File, target: File, block: (PDDocument) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    block(document)
                    document.save(target)
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }
}
