import aktarjabed.com.jaascanner.pdf.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory as DesktopJpeg

class DesktopRecoder : ImageRecoder {
    override fun recode(document: PDDocument, image: PDImageXObject, maxSidePx: Int, jpegQuality: Float): PDImageXObject? {
        val src = image.image ?: return null
        val longest = maxOf(src.width, src.height)
        val use = if (longest > maxSidePx) {
            val f = maxSidePx.toFloat() / longest
            val w = (src.width * f).toInt().coerceAtLeast(1); val h = (src.height * f).toInt().coerceAtLeast(1)
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(src, 0, 0, w, h, null); g.dispose(); out
        } else src
        return DesktopJpeg.createFromImage(document, use, jpegQuality)
    }
}

fun main() = runBlocking {
    fun f(n: String) = File("pdf/$n")
    // ---- compress
    for (q in listOf(80, 50, 25)) {
        val r = PdfImageCompress.compress(f("photos.pdf"), f("photos_q$q.pdf"), q, DesktopRecoder())
        println("compress q=$q: ${r.originalBytes / 1024} KB -> ${r.newBytes / 1024} KB, pictures ${r.picturesFound}, shrunk ${r.picturesShrunk}")
    }
    val rs = PdfImageCompress.compress(f("scan.pdf"), f("scan_c.pdf"), 40, DesktopRecoder())
    println("scan.pdf q=40: ${rs.originalBytes / 1024} KB -> ${rs.newBytes / 1024} KB, found ${rs.picturesFound}, shrunk ${rs.picturesShrunk}")
    val rb = PdfImageCompress.compress(f("book.pdf"), f("book_c.pdf"), 40, DesktopRecoder())
    println("book.pdf (no pictures): found ${rb.picturesFound}, shrunk ${rb.picturesShrunk}")

    // ---- bookmarks
    PdfBookmarks.add(f("book.pdf"), f("bm1.pdf"), "Start", 0)
    PdfBookmarks.add(f("bm1.pdf"), f("bm2.pdf"), "  Middle part  ", 3)
    PdfBookmarks.add(f("bm2.pdf"), f("bm3.pdf"), "", 5)
    println("bookmarks: " + PdfBookmarks.read(f("bm3.pdf")))
    PdfBookmarks.removeAt(f("bm3.pdf"), f("bm4.pdf"), 1)
    println("after removing #2: " + PdfBookmarks.read(f("bm4.pdf")))
    try { PdfBookmarks.add(f("book.pdf"), f("bm_bad.pdf"), "x", 50) } catch (e: Throwable) { println("bad page -> ${e.message}") }
    try { PdfBookmarks.removeAt(f("bm4.pdf"), f("bm_bad2.pdf"), 9) } catch (e: Throwable) { println("bad index -> ${e.message}") }
    println("bad outputs left behind: ${f("bm_bad.pdf").exists()} ${f("bm_bad2.pdf").exists()}")
    // nested
    PDDocument.load(f("book.pdf")).use { d ->
        PdfBookmarks.writeTo(d, listOf(Bookmark("A", 0, 0), Bookmark("A1", 1, 1), Bookmark("A1a", 2, 2), Bookmark("A2", 3, 1), Bookmark("B", 4, 0), Bookmark("jump", 9, 0), Bookmark("deep", 5, 7)))
        d.save(f("bm_nested.pdf"))
    }
    println("nested: " + PdfBookmarks.read(f("bm_nested.pdf")))
    PdfBookmarks.removeAt(f("bm_nested.pdf"), f("bm_nested2.pdf"), 0)
    println("nested minus A: " + PdfBookmarks.read(f("bm_nested2.pdf")))

    // ---- notes
    PDDocument.load(f("book.pdf")).use { d ->
        PdfNotes.addNote(d, 0, 0.5f, 0.4f, "Check this figure with Rahim", "Kuku")
        PdfNotes.addNote(d, 2, 0.9f, 0.05f, "Corner note near the edge")
        PdfNotes.addNote(d, 2, 0.1f, 0.95f, "x".repeat(5000))
        d.save(f("notes1.pdf"))
    }
    val ns = PdfNotes.read(f("notes1.pdf"))
    ns.forEach { println("note p${it.page + 1} #${it.index} ${it.kind} by '${it.author}' len=${it.text.length}: ${it.text.take(40)}") }
    PdfNotes.remove(f("notes1.pdf"), f("notes2.pdf"), 0, 0)
    println("notes after removing first: " + PdfNotes.read(f("notes2.pdf")).size)
    try { PdfNotes.remove(f("notes1.pdf"), f("notes_bad.pdf"), 0, 5) } catch (e: Throwable) { println("bad note -> ${e.message}") }
    // rotated page note
    PDDocument.load(f("rot.pdf")).use { d -> PdfNotes.addNote(d, 0, 0.5f, 0.5f, "on a rotated page"); PdfNotes.addNote(d, 1, 0.5f, 0.5f, "on 180"); d.save(f("notes_rot.pdf")) }

    // ---- rearrange
    PdfTools.rearrange(f("book.pdf"), listOf(PagePlan(5), PagePlan(0), PagePlan(0, 90), PagePlan(2, 270), PagePlan(4)), f("rearr.pdf"))
    try { PdfTools.rearrange(f("book.pdf"), listOf(PagePlan(7)), f("rearr_bad.pdf")) } catch (e: Throwable) { println("bad plan -> ${e.message}") }
    try { PdfTools.rearrange(f("book.pdf"), emptyList(), f("rearr_bad.pdf")) } catch (e: Throwable) { println("empty plan -> ${e.message}") }
    try { PdfTools.rearrange(f("book.pdf"), listOf(PagePlan(1, 45)), f("rearr_bad.pdf")) } catch (e: Throwable) { println("45 degrees -> ${e.message}") }
    PdfTools.rearrange(f("rot.pdf"), listOf(PagePlan(0, 90), PagePlan(0, 0), PagePlan(1, 180)), f("rearr_rot.pdf"))
    Unit
}
