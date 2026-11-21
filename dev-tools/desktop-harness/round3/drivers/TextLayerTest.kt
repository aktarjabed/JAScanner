import aktarjabed.com.jaascanner.pdf.*
import java.io.File
import kotlinx.coroutines.runBlocking

fun readLines(path: String): Map<Int, List<RecognisedLine>> =
    File(path).readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        .groupBy({ it[0].toInt() }, { RecognisedLine(it[1], it[2].toFloat(), it[3].toFloat(), it[4].toFloat(), it[5].toFloat()) })

fun main() = runBlocking {
    val lines = readLines("pdf/scan_boxes.tsv")
    val n = PdfTextLayer.add(File("pdf/scan.pdf"), File("pdf/scan_text.pdf"), lines)
    println("lines written: $n of ${lines.values.sumOf { it.size }}")
    // Mixed in awkward input: blank text, zero-size box, accented, unencodable, out-of-range page, newline inside
    val odd = mapOf(
        0 to listOf(
            RecognisedLine("", 0.1f, 0.1f, 0.5f, 0.12f),
            RecognisedLine("zero box", 0.3f, 0.3f, 0.3f, 0.3f),
            RecognisedLine("Café déjà vu naïve", 0.1f, 0.5f, 0.6f, 0.53f),
            RecognisedLine("Price ₹500 only", 0.1f, 0.6f, 0.5f, 0.63f),
            RecognisedLine("two\nlines", 0.1f, 0.7f, 0.5f, 0.73f),
            RecognisedLine("☃☃☃", 0.1f, 0.8f, 0.5f, 0.83f),
        ),
        99 to listOf(RecognisedLine("nowhere", 0.1f, 0.1f, 0.5f, 0.12f)),
    )
    val m = PdfTextLayer.add(File("pdf/scan.pdf"), File("pdf/scan_odd.pdf"), odd)
    println("odd input written: $m")
}
