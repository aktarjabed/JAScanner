package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A PDF opened for on-screen reading: knows every page's shape up front (so lists can reserve
 * the right height) and caches recently rendered pages. Used by the viewer and the editor.
 */
class PdfPageSource private constructor(
    private val renderer: PdfPageRenderer,
    val sizes: List<PageSizePt>,
) {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    val pageCount: Int get() = sizes.size

    /** Height divided by width of the page as displayed. */
    fun heightOverWidth(index: Int): Float {
        val size = sizes.getOrNull(index) ?: return DEFAULT_RATIO
        return if (size.width > 0) size.height.toFloat() / size.width else DEFAULT_RATIO
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val width = widthPx.coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX)
        val key = "$index:$width"
        cache.get(key)?.let { return it }
        val bitmap = renderer.render(index, width) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    suspend fun release() {
        withContext(NonCancellable) { renderer.release() }
        cache.evictAll()
    }

    companion object {
        private const val CACHE_BYTES = 64 * 1024 * 1024
        private const val MIN_WIDTH_PX = 200
        private const val MAX_WIDTH_PX = 2048
        private const val DEFAULT_RATIO = 1.414f

        /** Returns null if the file cannot be opened (damaged, or password-protected). */
        suspend fun open(file: File): PdfPageSource? {
            val renderer = PdfPageRenderer.open(file) ?: return null
            if (renderer.pageCount == 0) {
                renderer.release()
                return null
            }
            val sizes = List(renderer.pageCount) { index -> renderer.pageSize(index) ?: PageSizePt(595, 842) }
            return PdfPageSource(renderer, sizes)
        }
    }
}
