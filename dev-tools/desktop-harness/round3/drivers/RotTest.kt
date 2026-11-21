import aktarjabed.com.jaascanner.pdf.*
import java.io.File
import kotlinx.coroutines.runBlocking
fun main() = runBlocking {
    val l = listOf(RecognisedLine("Hello rotated world", 0.2f, 0.30f, 0.8f, 0.34f))
    PdfTextLayer.add(File("pdf/rot.pdf"), File("pdf/rot_text.pdf"), mapOf(0 to l, 1 to l, 2 to l)); Unit
}
