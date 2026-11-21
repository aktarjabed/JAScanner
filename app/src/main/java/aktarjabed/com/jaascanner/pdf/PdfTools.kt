package aktarjabed.com.jaascanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import aktarjabed.com.jaascanner.core.CardLayout
import aktarjabed.com.jaascanner.core.PdfCoordinateMapper
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An error whose message is safe to show to the user as it is. */
class PdfToolException(message: String) : Exception(message)

fun Throwable.toUserMessage(): String = when {
    this is PdfToolException -> message ?: "Something went wrong."
    this is InvalidPasswordException -> "The password is incorrect."
    this is OutOfMemoryError -> "Not enough memory for this file. Try a smaller PDF."
    message?.contains("password", ignoreCase = true) == true -> "This PDF is password-protected. Unlock it first."
    this is IOException -> "Could not read or write the file. If you opened it, the PDF may be damaged."
    else -> "Something went wrong (${javaClass.simpleName})."
}

enum class PageSizeOption(val label: String) {
    Fit("Match each image"),
    A0("A0"),
    A1("A1"),
    A2("A2"),
    A3("A3"),
    A4("A4"),
    A5("A5"),
    A6("A6"),
    Letter("US Letter");

    /** The fixed page rectangle for this size, or null for [Fit]. */
    fun pageRect(): PDRectangle? = when (this) {
        Fit -> null
        A0 -> PDRectangle.A0
        A1 -> PDRectangle.A1
        A2 -> PDRectangle.A2
        A3 -> PDRectangle.A3
        A4 -> PDRectangle.A4
        A5 -> PDRectangle.A5
        A6 -> PDRectangle.A6
        Letter -> PDRectangle.LETTER
    }
}

data class CompressOutcome(val originalBytes: Long, val newBytes: Long)

/** One page of a rearranged document: which original page, and an extra clockwise turn in degrees. */
data class PagePlan(val source: Int, val rotation: Int = 0)

/** Page-level PDF operations. Every function writes a new file and never touches its source. */
object PdfTools {

    private const val A4_MARGIN_PT = 18f
    private const val FIT_LONG_SIDE_PT = 842f

    private suspend fun produce(target: File, block: () -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    private suspend fun edit(source: File, target: File, password: String = "", block: (PDDocument) -> Unit) {
        produce(target) {
            PDDocument.load(source, password).use { document ->
                block(document)
                document.save(target)
            }
        }
    }

    /** Every chosen page must exist; a typo must never silently produce an empty or partial result. */
    private fun requirePagesExist(document: PDDocument, pages: Collection<Int>) {
        val count = document.numberOfPages
        val bad = pages.firstOrNull { it !in 0 until count } ?: return
        throw PdfToolException("Page ${bad + 1} does not exist. This document has $count pages.")
    }

    suspend fun merge(sources: List<File>, target: File) {
        if (sources.size < 2) throw PdfToolException("Pick at least two PDFs to merge.")
        produce(target) {
            val merger = PDFMergerUtility()
            sources.forEach { merger.addSource(it) }
            merger.destinationFileName = target.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        }
    }

    /** Keeps only [pages] (zero-based), in their original order. */
    suspend fun extractPages(source: File, pages: List<Int>, target: File) {
        if (pages.isEmpty()) throw PdfToolException("Choose at least one page.")
        val keep = pages.toSet()
        edit(source, target) { document ->
            requirePagesExist(document, keep)
            for (index in document.numberOfPages - 1 downTo 0) {
                if (index !in keep) document.removePage(index)
            }
        }
    }

    suspend fun deletePages(source: File, pages: List<Int>, target: File) {
        if (pages.isEmpty()) throw PdfToolException("Choose at least one page to delete.")
        val remove = pages.toSet()
        edit(source, target) { document ->
            requirePagesExist(document, remove)
            if (remove.size >= document.numberOfPages) {
                throw PdfToolException("You cannot delete every page of a document.")
            }
            for (index in document.numberOfPages - 1 downTo 0) {
                if (index in remove) document.removePage(index)
            }
        }
    }

    suspend fun rotatePages(source: File, pages: List<Int>, degrees: Int, target: File) {
        if (pages.isEmpty()) throw PdfToolException("Choose at least one page.")
        val rotate = pages.toSet()
        edit(source, target) { document ->
            requirePagesExist(document, rotate)
            for (index in rotate) {
                val page = document.getPage(index)
                page.rotation = PdfCoordinateMapper.normalizeRotation(page.rotation + degrees)
            }
        }
    }

    /** Splits into files of [pagesPerFile] pages each. [newFile] supplies each output file. */
    suspend fun splitEvery(source: File, pagesPerFile: Int, newFile: () -> File): List<File> =
        withContext(Dispatchers.IO) {
            if (pagesPerFile < 1) throw PdfToolException("Pages per file must be at least 1.")
            val outputs = mutableListOf<File>()
            try {
                PDDocument.load(source).use { document ->
                    val splitter = Splitter()
                    splitter.setSplitAtPage(pagesPerFile)
                    val parts = splitter.split(document)
                    try {
                        for (part in parts) {
                            val file = newFile()
                            outputs.add(file)
                            part.save(file)
                        }
                    } finally {
                        parts.forEach { runCatching { it.close() } }
                    }
                }
                outputs
            } catch (error: Throwable) {
                outputs.forEach { it.delete() }
                throw error
            }
        }

    suspend fun protect(source: File, password: String, target: File) {
        if (password.length < 4) throw PdfToolException("Use a password of at least 4 characters.")
        edit(source, target) { document ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            policy.setPreferAES(true)
            document.protect(policy)
        }
    }

    suspend fun unlock(source: File, password: String, target: File) {
        edit(source, target, password) { document ->
            if (!document.isEncrypted) throw PdfToolException("This PDF is not password-protected.")
            document.isAllSecurityToBeRemoved = true
        }
    }

    /**
     * Rebuilds the PDF from JPEG renders of every page. This makes scans much smaller, but the
     * result is image-only: any text layer is lost, so the caller should re-run OCR if needed.
     */
    suspend fun compress(
        source: File,
        target: File,
        quality: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CompressOutcome {
        val renderer = PdfPageRenderer.open(source)
            ?: throw PdfToolException("Could not open this PDF. If it is password-protected, unlock it first.")
        try {
            val total = renderer.pageCount
            if (total == 0) throw PdfToolException("This PDF has no pages.")
            val dpi = (quality * 1.8f).coerceIn(72f, 170f)
            val jpegQuality = (quality / 100f).coerceIn(0.2f, 0.95f)
            // Pages are rendered one by one (the renderer is suspending), then appended to the output.
            withContext(Dispatchers.IO) {
                try {
                    PDDocument().use { output ->
                        for (index in 0 until total) {
                            val size = renderer.pageSize(index) ?: throw PdfToolException("Could not read page ${index + 1}.")
                            val widthPx = min(1600, (size.width * dpi / 72f).roundToInt()).coerceAtLeast(200)
                            val bitmap = renderer.render(index, widthPx)
                                ?: throw PdfToolException("Could not render page ${index + 1}.")
                            try {
                                val page = PDPage(PDRectangle(size.width.toFloat(), size.height.toFloat()))
                                output.addPage(page)
                                val image = JPEGFactory.createFromImage(output, bitmap, jpegQuality)
                                PDPageContentStream(output, page).use { content ->
                                    content.drawImage(image, 0f, 0f, size.width.toFloat(), size.height.toFloat())
                                }
                            } finally {
                                bitmap.recycle()
                            }
                            onProgress(index + 1, total)
                        }
                        output.save(target)
                    }
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { renderer.release() }
        }
        return CompressOutcome(source.length(), target.length())
    }

    suspend fun imagesToPdf(
        context: Context,
        uris: List<Uri>,
        option: PageSizeOption,
        target: File,
        filter: ImageFilter = ImageFilter.Original,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        if (uris.isEmpty()) throw PdfToolException("Pick at least one image.")
        produce(target) {
            PDDocument().use { output ->
                uris.forEachIndexed { index, uri ->
                    val raw = try {
                        ImageDecoding.decode(context, uri, maxSide = 2800)
                    } catch (error: Exception) {
                        throw PdfToolException("Image ${index + 1} could not be read.")
                    }
                    val flat = ImageDecoding.flattenOnWhite(raw)
                    val bitmap = ImageFilters.apply(flat, filter)
                    try {
                        addImagePage(output, bitmap, option)
                    } finally {
                        if (bitmap !== flat) bitmap.recycle()
                        flat.recycle()
                    }
                    onProgress(index + 1, uris.size)
                }
                output.save(target)
            }
        }
    }

    /**
     * Card scans (front, back, more sides) as a PDF: all sides stacked on one A4 page at real card
     * width, or one page per side.
     */
    suspend fun cardsToPdf(
        context: Context,
        uris: List<Uri>,
        onePage: Boolean,
        filter: ImageFilter,
        target: File,
    ) {
        if (uris.isEmpty()) throw PdfToolException("Scan or pick at least one side.")
        produce(target) {
            PDDocument().use { output ->
                val bitmaps = ArrayList<Bitmap>()
                try {
                    uris.forEachIndexed { index, uri ->
                        val raw = try {
                            ImageDecoding.decode(context, uri, maxSide = 2400)
                        } catch (error: Exception) {
                            throw PdfToolException("Side ${index + 1} could not be read.")
                        }
                        val flat = ImageDecoding.flattenOnWhite(raw)
                        val filtered = ImageFilters.apply(flat, filter)
                        if (filtered !== flat) flat.recycle()
                        bitmaps.add(filtered)
                    }
                    if (onePage) {
                        val page = PDPage(PDRectangle.A4)
                        output.addPage(page)
                        val spots = CardLayout.stack(
                            aspects = bitmaps.map { it.width.toFloat() / it.height },
                            pageWidth = PDRectangle.A4.width,
                            pageHeight = PDRectangle.A4.height,
                        )
                        PDPageContentStream(output, page).use { content ->
                            bitmaps.forEachIndexed { index, bitmap ->
                                val spot = spots[index]
                                val image = JPEGFactory.createFromImage(output, bitmap, 0.9f)
                                content.drawImage(image, spot.x, spot.y, spot.width, spot.height)
                            }
                        }
                    } else {
                        bitmaps.forEach { addImagePage(output, it, PageSizeOption.A6) }
                    }
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
                output.save(target)
            }
        }
    }

    /** Splits into one file per group of pages (zero-based indices). Groups may overlap. */
    suspend fun splitRanges(source: File, groups: List<List<Int>>, newFile: () -> File): List<File> =
        withContext(Dispatchers.IO) {
            if (groups.isEmpty()) throw PdfToolException("Enter at least one range.")
            val outputs = mutableListOf<File>()
            try {
                PDDocument.load(source).use { document ->
                    // Every sibling page tool validates its page numbers before touching anything;
                    // this one didn't, so a bad index used to throw an unvalidated PDFBox exception
                    // and surface as a generic "Something went wrong" instead of the same clear
                    // "Page N does not exist" message every other tool gives for the same mistake.
                    requirePagesExist(document, groups.flatten())
                    for (group in groups) {
                        if (group.isEmpty()) continue
                        val file = newFile()
                        outputs.add(file)
                        PDDocument().use { part ->
                            // importPage already appends the page to the new document. Resources inherited
                            // from the page tree must be copied by hand, the same way PDFBox's Splitter does.
                            for (index in group) {
                                val source = document.getPage(index)
                                val imported = part.importPage(source)
                                if (source.resources != null && !source.cosObject.containsKey(COSName.RESOURCES)) {
                                    imported.resources = source.resources
                                }
                            }
                            part.save(file)
                        }
                    }
                }
                outputs
            } catch (error: Throwable) {
                outputs.forEach { it.delete() }
                throw error
            }
        }

    /** Puts the pages in [order] (zero-based, a full permutation). */
    suspend fun reorderPages(source: File, order: List<Int>, target: File) {
        edit(source, target) { document ->
            val count = document.numberOfPages
            if (order.size != count || order.toSet().size != count) {
                throw PdfToolException("The new order must list every page exactly once.")
            }
            val pages = (0 until count).map { document.getPage(it) }
            for (index in count - 1 downTo 0) document.removePage(index)
            for (index in order) document.addPage(pages[index])
        }
    }

    /**
     * Builds a new document from [plan], in order: each entry names a page of [source] (zero-based)
     * and an extra clockwise turn. A page may appear more than once (a duplicate) or not at all
     * (deleted). This is what the page organiser saves with.
     */
    suspend fun rearrange(source: File, plan: List<PagePlan>, target: File) {
        if (plan.isEmpty()) throw PdfToolException("A document needs at least one page.")
        if (plan.any { it.rotation % 90 != 0 }) throw PdfToolException("Pages can only be turned in steps of 90 degrees.")
        edit(source, target) { document ->
            requirePagesExist(document, plan.map { it.source })
            val originals = (0 until document.numberOfPages).map { document.getPage(it) }
            val baseRotation = originals.map { it.rotation }
            for (index in document.numberOfPages - 1 downTo 0) document.removePage(index)
            val used = HashSet<Int>()
            for (entry in plan) {
                val original = originals[entry.source]
                val page = if (used.add(entry.source)) {
                    document.addPage(original)
                    original
                } else {
                    // A second use of the same page: import a copy (without the notes and form
                    // widgets, which must not appear twice).
                    val copy = document.importPage(original)
                    if (original.resources != null && !original.cosObject.containsKey(COSName.RESOURCES)) {
                        copy.resources = original.resources
                    }
                    copy.annotations = emptyList()
                    copy
                }
                page.rotation = PdfCoordinateMapper.normalizeRotation(baseRotation[entry.source] + entry.rotation)
            }
        }
    }

    /** Inserts [count] blank pages of [option] size after page [afterPage] (1-based; 0 means at the start). */
    suspend fun insertBlankPages(source: File, afterPage: Int, count: Int, option: PageSizeOption, target: File) {
        if (count < 1) throw PdfToolException("Insert at least one page.")
        val rect = option.pageRect() ?: PDRectangle.A4
        edit(source, target) { document ->
            if (afterPage < 0 || afterPage > document.numberOfPages) {
                throw PdfToolException("Choose a position between 0 and ${document.numberOfPages}.")
            }
            val pages = (0 until document.numberOfPages).map { document.getPage(it) }
            val blanks = List(count) { PDPage(PDRectangle(rect.width, rect.height)) }
            for (index in document.numberOfPages - 1 downTo 0) document.removePage(index)
            pages.take(afterPage).forEach { document.addPage(it) }
            blanks.forEach { document.addPage(it) }
            pages.drop(afterPage).forEach { document.addPage(it) }
        }
    }

    /**
     * Inserts [pages] (zero-based) of [insertFrom] into [source] after page [afterPage] (1-based; 0 = at the start).
     * Both files are left untouched; the result goes to [target].
     */
    suspend fun insertFromPdf(source: File, insertFrom: File, pages: List<Int>, afterPage: Int, target: File) {
        if (pages.isEmpty()) throw PdfToolException("Choose at least one page to insert.")
        produce(target) {
            PDDocument.load(insertFrom).use { extra ->
                PDDocument.load(source).use { document ->
                    if (afterPage < 0 || afterPage > document.numberOfPages) {
                        throw PdfToolException("Choose a position between 0 and ${document.numberOfPages}.")
                    }
                    requirePagesExist(extra, pages)
                    val originals = (0 until document.numberOfPages).map { document.getPage(it) }
                    for (index in document.numberOfPages - 1 downTo 0) document.removePage(index)
                    originals.take(afterPage).forEach { document.addPage(it) }
                    // importPage appends to the end of the document, which is exactly where these belong now.
                    for (index in pages) {
                        val page = extra.getPage(index)
                        val imported = document.importPage(page)
                        if (page.resources != null && !page.cosObject.containsKey(COSName.RESOURCES)) {
                            imported.resources = page.resources
                        }
                    }
                    originals.drop(afterPage).forEach { document.addPage(it) }
                    document.save(target)
                }
            }
        }
    }

    /** Trims [percent] of the width and height from each side of the chosen pages (via the crop box). */
    suspend fun cropPages(source: File, pages: List<Int>, percent: Int, target: File) {
        if (pages.isEmpty()) throw PdfToolException("Choose at least one page.")
        if (percent !in 1..30) throw PdfToolException("Trim between 1% and 30%.")
        val fraction = percent / 100f
        edit(source, target) { document ->
            requirePagesExist(document, pages)
            for (index in pages.toSet()) {
                val page = document.getPage(index)
                val box = page.cropBox ?: page.mediaBox
                val dx = box.width * fraction
                val dy = box.height * fraction
                page.cropBox = PDRectangle(
                    box.lowerLeftX + dx,
                    box.lowerLeftY + dy,
                    box.width - 2 * dx,
                    box.height - 2 * dy,
                )
            }
        }
    }

    private fun addImagePage(output: PDDocument, bitmap: Bitmap, option: PageSizeOption) {
        val imageW = bitmap.width.toFloat()
        val imageH = bitmap.height.toFloat()
        val page: PDPage
        val drawX: Float
        val drawY: Float
        val drawW: Float
        val drawH: Float
        if (option == PageSizeOption.Fit) {
            val scale = FIT_LONG_SIDE_PT / max(imageW, imageH)
            drawW = imageW * scale
            drawH = imageH * scale
            drawX = 0f
            drawY = 0f
            page = PDPage(PDRectangle(drawW, drawH))
        } else {
            val base = option.pageRect() ?: PDRectangle.A4
            val landscape = imageW > imageH
            val pageW = if (landscape) base.height else base.width
            val pageH = if (landscape) base.width else base.height
            val availW = pageW - 2 * A4_MARGIN_PT
            val availH = pageH - 2 * A4_MARGIN_PT
            val scale = min(availW / imageW, availH / imageH)
            drawW = imageW * scale
            drawH = imageH * scale
            drawX = (pageW - drawW) / 2f
            drawY = (pageH - drawH) / 2f
            page = PDPage(PDRectangle(pageW, pageH))
        }
        output.addPage(page)
        val image = JPEGFactory.createFromImage(output, bitmap, 0.85f)
        PDPageContentStream(output, page).use { content ->
            content.drawImage(image, drawX, drawY, drawW, drawH)
        }
    }
}
