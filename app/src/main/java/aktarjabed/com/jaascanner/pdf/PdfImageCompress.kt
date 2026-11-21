package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.util.IdentityHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Re-encodes one picture; the Android build uses bitmaps, the desktop test harness uses BufferedImage. */
fun interface ImageRecoder {
    /** A smaller stand-in for [image] (longest side at most [maxSidePx]), or null to leave it alone. */
    fun recode(document: PDDocument, image: PDImageXObject, maxSidePx: Int, jpegQuality: Float): PDImageXObject?
}

data class ImageCompressReport(
    val originalBytes: Long,
    val newBytes: Long,
    val picturesFound: Int,
    val picturesShrunk: Int,
)

/**
 * Makes a PDF smaller by re-compressing the pictures inside it and leaving everything else
 * (text, vector drawings, bookmarks, forms, comments) exactly as it was. Unlike rebuilding every
 * page as a picture, the text stays real text.
 */
object PdfImageCompress {

    private const val MIN_PIXELS = 160_000L
    private const val MIN_GAIN = 0.9
    private const val MAX_FORM_DEPTH = 4

    /** Pixel limit for the longest side of a picture, from the 20..95 quality setting. */
    fun maxSideFor(quality: Int): Int = (quality.coerceIn(10, 100) / 100f * 2600f).toInt().coerceIn(800, 2400)

    fun jpegQualityFor(quality: Int): Float = (quality / 100f).coerceIn(0.2f, 0.95f)

    suspend fun compress(
        source: File,
        target: File,
        quality: Int,
        recoder: ImageRecoder,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImageCompressReport = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(source).use { document ->
                val work = Work(document, maxSideFor(quality), jpegQualityFor(quality), recoder)
                val total = document.numberOfPages
                for (index in 0 until total) {
                    work.visit(document.getPage(index).resources, 0)
                    onProgress(index + 1, total)
                }
                document.save(target)
                ImageCompressReport(source.length(), target.length(), work.found, work.shrunk)
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private class Work(
        val document: PDDocument,
        val maxSide: Int,
        val jpegQuality: Float,
        val recoder: ImageRecoder,
    ) {
        var found = 0
        var shrunk = 0
        private val decided = IdentityHashMap<COSBase, PDImageXObject?>()
        private val forms = IdentityHashMap<COSBase, Boolean>()

        fun visit(resources: PDResources?, depth: Int) {
            if (resources == null) return
            val names = resources.xObjectNames.toList()
            for (name in names) {
                val xobject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                when (xobject) {
                    is PDImageXObject -> replaceIfSmaller(resources, name, xobject)
                    is PDFormXObject -> if (depth < MAX_FORM_DEPTH && forms.put(xobject.cosObject, true) == null) {
                        visit(xobject.resources, depth + 1)
                    }
                    else -> Unit
                }
            }
        }

        private fun replaceIfSmaller(resources: PDResources, name: COSName, image: PDImageXObject) {
            val key = image.cosObject
            val replacement = if (decided.containsKey(key)) {
                decided[key]
            } else {
                found++
                shrink(image).also {
                    decided[key] = it
                    if (it != null) shrunk++
                }
            }
            if (replacement != null) resources.put(name, replacement)
        }

        private fun shrink(image: PDImageXObject): PDImageXObject? {
            if (image.isStencil) return null
            if (image.width.toLong() * image.height < MIN_PIXELS) return null
            if (image.cosObject.containsKey(COSName.MASK) || image.cosObject.containsKey(COSName.SMASK)) return null
            val before = rawSize(image)
            val candidate = runCatching { recoder.recode(document, image, maxSide, jpegQuality) }.getOrNull() ?: return null
            val after = rawSize(candidate)
            return if (before > 0 && after > 0 && after < before * MIN_GAIN) candidate else null
        }

        private fun rawSize(image: PDImageXObject): Long = runCatching {
            image.cosObject.createRawInputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        }.getOrDefault(0L)
    }
}
