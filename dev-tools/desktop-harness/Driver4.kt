import aktarjabed.com.jaascanner.pdf.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument

fun main() = runBlocking {
    val src = File("pdf/lo.pdf")
    val words = PDDocument.load(src).use { PdfTextRuns.analyse(it, 0).words }
    fun mark(text: String, new: String) = TextEditMark(words.first { it.text == text }, new, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), null)
    // 'q' and 'z' are not in the subset font: forces the overlay path. Also an in-place one.
    val report = PdfEditor.apply(src, File("pdf/o4.pdf"), mapOf(0 to listOf(
        mark("Rahim", "Rahqz"), mark("Karim", "Karím"), mark("Cachar.", "Cachar,"), mark("ABC-2026-0045", "ABC-2026-0099"), mark("Agreement", "Agreemxnt"))))
    println(report)
}
