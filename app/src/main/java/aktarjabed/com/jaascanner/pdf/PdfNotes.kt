package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.PageBox
import aktarjabed.com.jaascanner.core.PdfCoordinateMapper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A comment found in a PDF. [index] is its position in the page's annotation list. */
data class NoteInfo(
    val page: Int,
    val index: Int,
    val kind: String,
    val text: String,
    val author: String,
)

/**
 * Sticky-note comments as real PDF annotations: any reader (Acrobat included) shows the note icon
 * and its text, and this app lists them with a jump to the page.
 */
object PdfNotes {

    const val MAX_TEXT = 2000
    private const val ICON_SIZE_PT = 24f
    private const val MAX_NOTES = 5000

    /** Annotation kinds that carry a comment worth listing. Links, form widgets and pop-ups do not. */
    private val COMMENT_KINDS = setOf(
        "Text", "FreeText", "Highlight", "Underline", "StrikeOut", "Squiggly", "Square", "Circle", "Line", "Ink", "Stamp",
    )

    fun read(document: PDDocument): List<NoteInfo> {
        val result = ArrayList<NoteInfo>()
        for (pageIndex in 0 until document.numberOfPages) {
            if (result.size >= MAX_NOTES) break
            val annotations = runCatching { document.getPage(pageIndex).annotations }.getOrNull() ?: continue
            for ((index, annotation) in annotations.withIndex()) {
                val kind = annotation.subtype ?: continue
                if (kind !in COMMENT_KINDS) continue
                val text = annotation.contents?.trim().orEmpty()
                if (text.isEmpty()) continue
                val author = (annotation as? PDAnnotationMarkup)?.titlePopup?.trim().orEmpty()
                result.add(NoteInfo(pageIndex, index, kind, text, author))
            }
        }
        return result
    }

    suspend fun read(file: File): List<NoteInfo> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { read(it) }
    }

    /**
     * Adds a sticky note whose icon sits at ([u], [v]) on the page as the reader sees it (both 0..1,
     * v downwards). The icon keeps its size and stays upright when the page is zoomed or turned.
     */
    fun addNote(document: PDDocument, pageIndex: Int, u: Float, v: Float, text: String, author: String = "JAScanner") {
        require(pageIndex in 0 until document.numberOfPages) { "No such page" }
        val page = document.getPage(pageIndex)
        val crop = page.cropBox
        val box = PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
        val anchor = PdfCoordinateMapper.point(box, page.rotation, u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        val note = PDAnnotationText()
        note.contents = text.trim().take(MAX_TEXT)
        note.titlePopup = author
        note.name = PDAnnotationText.NAME_NOTE
        // coerceIn throws if its upper bound is below its lower bound, which a page narrower or
        // shorter than the icon (a malformed or deliberately tiny crop box) would do here; clamping
        // each upper bound to be at least the lower bound keeps this safe on any page size while
        // leaving normal-sized pages' placement unchanged.
        note.rectangle = PDRectangle(
            (anchor.x - ICON_SIZE_PT / 2f).coerceIn(box.minX, (box.minX + box.width - ICON_SIZE_PT).coerceAtLeast(box.minX)),
            (anchor.y - ICON_SIZE_PT / 2f).coerceIn(box.minY, (box.minY + box.height - ICON_SIZE_PT).coerceAtLeast(box.minY)),
            ICON_SIZE_PT,
            ICON_SIZE_PT,
        )
        note.color = PDColor(floatArrayOf(1f, 0.85f, 0.1f), PDDeviceRGB.INSTANCE)
        note.isPrinted = true
        note.isNoZoom = true
        note.isNoRotate = true
        note.constructAppearances(document)
        val list = page.annotations
        list.add(note)
        page.annotations = list
    }

    /** Removes the annotation at [index] of [page] (zero-based), as listed by [read]. */
    suspend fun remove(source: File, target: File, page: Int, index: Int) {
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    removeAt(document, page, index)
                    document.save(target)
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    /**
     * Removes [note] by finding it in the document as it is now. Matching by content instead of
     * the remembered annotation index keeps a second quick removal from deleting a different
     * comment after the first one shifted the indices.
     */
    suspend fun remove(source: File, target: File, note: NoteInfo) {
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    val current = read(document)
                    val match = current.firstOrNull {
                        it.page == note.page && it.kind == note.kind &&
                            it.text == note.text && it.author == note.author
                    } ?: throw PdfToolException("That comment no longer exists.")
                    removeAt(document, match.page, match.index)
                    document.save(target)
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    private fun removeAt(document: PDDocument, page: Int, index: Int) {
        if (page !in 0 until document.numberOfPages) throw PdfToolException("That page no longer exists.")
        val pdPage = document.getPage(page)
        val list = pdPage.annotations
        if (index !in list.indices) throw PdfToolException("That comment no longer exists.")
        list.removeAt(index)
        pdPage.annotations = list
    }
}
