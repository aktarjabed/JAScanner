package aktarjabed.com.jaascanner.pdf

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.TreeSet

/** Sends an existing PDF file to the system print dialog (which also offers "Save as PDF"). */
class PdfFilePrintAdapter(
    private val file: File,
    private val jobName: String,
    private val pageCount: Int,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(if (pageCount > 0) pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            if (pages.isNullOrEmpty() || pages.any { it.start == 0 && it.end == Int.MAX_VALUE }) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                return
            }
            var writtenRanges: Array<PageRange> = arrayOf(PageRange.ALL_PAGES)
            PDDocument.load(file).use { document ->
                val count = document.numberOfPages
                val selected = TreeSet<Int>()
                for (range in pages) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    // PageRange is 0-based and inclusive. Clamp to the real page count so an
                    // open-ended range cannot expand into billions of indices.
                    val start = maxOf(0, range.start)
                    val end = minOf(count - 1, range.end)
                    if (start <= end) for (page in start..end) selected.add(page)
                }
                if (selected.isEmpty()) {
                    callback.onWriteFailed("No pages were selected.")
                    return
                }
                // Remove from the end so the remaining indices stay valid. This only changes the
                // in-memory copy; the original file is never written.
                for (page in (0 until count).filterNot { it in selected }.sortedDescending()) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    document.removePage(page)
                }
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                FileOutputStream(destination.fileDescriptor).use { output ->
                    document.save(output)
                    output.flush()
                }
                writtenRanges = rangesOf(selected)
            }
            callback.onWriteFinished(writtenRanges)
        } catch (error: Exception) {
            callback.onWriteFailed(error.message ?: "Could not print the document.")
        }
    }

    /** Turns a set of page indices into the smallest list of contiguous ranges. */
    private fun rangesOf(pages: Set<Int>): Array<PageRange> {
        val result = ArrayList<PageRange>()
        var start = -1
        var previous = -1
        for (page in pages.sorted()) {
            if (start < 0) {
                start = page
            } else if (page != previous + 1) {
                result.add(PageRange(start, previous))
                start = page
            }
            previous = page
        }
        if (start >= 0) result.add(PageRange(start, previous))
        return result.toTypedArray()
    }

    companion object {
        fun print(activity: Activity, file: File, jobName: String, pageCount: Int) {
            val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(jobName, PdfFilePrintAdapter(file, jobName, pageCount), PrintAttributes.Builder().build())
        }
    }
}
