package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Page size in PDF points (1/72 inch), as reported by the platform renderer. */
data class PageSizePt(val width: Int, val height: Int)

/**
 * Thin wrapper around the platform [PdfRenderer]. The platform allows only one page to be open
 * at a time, so every call is serialised with a mutex. Returns null instead of throwing when a
 * page cannot be rendered.
 */
class PdfPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    suspend fun pageSize(index: Int): PageSizePt? = mutex.withLock {
        if (closed || index !in 0 until pageCount) return@withLock null
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(index)
                try {
                    PageSizePt(page.width, page.height)
                } finally {
                    page.close()
                }
            } catch (error: Exception) {
                null
            }
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (closed || index !in 0 until pageCount) return@withLock null
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(index)
                try {
                    val width = widthPx.coerceAtLeast(1)
                    val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            } catch (error: Exception) {
                null
            } catch (error: OutOfMemoryError) {
                null
            }
        }
    }

    suspend fun release() {
        mutex.withLock { closeNow() }
    }

    private fun closeNow() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** Returns null if the file is missing, damaged or password-protected. */
        fun open(file: File): PdfPageRenderer? {
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (error: Exception) {
                return null
            }
            return try {
                PdfPageRenderer(descriptor, PdfRenderer(descriptor))
            } catch (error: Exception) {
                runCatching { descriptor.close() }
                null
            }
        }

        fun pageCountOrZero(file: File): Int {
            val renderer = open(file) ?: return 0
            val count = renderer.pageCount
            renderer.closeNow()
            return count
        }
    }
}
