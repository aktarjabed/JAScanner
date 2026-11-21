package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the embedded text layer of a PDF (not OCR). Scanned pages without a text layer return "". */
object PdfTextExtractor {

    suspend fun pageTexts(file: File, password: String = "", maxPages: Int = Int.MAX_VALUE): List<String> =
        withContext(Dispatchers.IO) {
            PDDocument.load(file, password).use { document ->
                val stripper = PDFTextStripper()
                val total = minOf(document.numberOfPages, maxPages)
                List(total) { index ->
                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    stripper.getText(document).trim()
                }
            }
        }

    /** Zero-based indices of the pages whose text contains [query] (case-insensitive). */
    fun pagesContaining(texts: List<String>, query: String): List<Int> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return texts.indices.filter { texts[it].contains(needle, ignoreCase = true) }
    }
}
