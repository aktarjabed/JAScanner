import aktarjabed.com.jaascanner.pdf.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument

inline fun timed(label: String, block: () -> Unit) {
    val t = System.currentTimeMillis()
    try { block(); println("$label OK ${System.currentTimeMillis() - t} ms") } catch (e: Throwable) { println("$label FAIL ${e.javaClass.simpleName}: ${e.toUserMessage()} (${System.currentTimeMillis() - t} ms)") }
}

fun main() = runBlocking {
    val d = "pdf/"; val big = File(d + "big500.pdf"); val five = File(d + "five.pdf")
    fun out(n: String) = File(d + "o3_" + n + ".pdf")
    println("heap max MB = ${Runtime.getRuntime().maxMemory() / 1024 / 1024}")
    timed("merge 500+500") { PdfTools.merge(listOf(big, big), out("merge1000")) }
    timed("extract 250") { PdfTools.extractPages(big, (0 until 500 step 2).toList(), out("ext")) }
    timed("delete 100") { PdfTools.deletePages(big, (0 until 100).toList(), out("del")) }
    timed("reorder reverse") { PdfTools.reorderPages(big, (499 downTo 0).toList(), out("rev")) }
    timed("insert blank 20") { PdfTools.insertBlankPages(big, 250, 20, PageSizeOption.A4, out("blank")) }
    timed("rotate all") { PdfTools.rotatePages(big, (0 until 500).toList(), 90, out("rot")) }
    timed("crop all") { PdfTools.cropPages(big, (0 until 500).toList(), 5, out("crop")) }
    timed("split every 50") { var n = 0; PdfTools.splitEvery(big, 50) { File(d + "o3_split${n++}.pdf") } }
    timed("protect") { PdfTools.protect(big, "pw", out("prot")) }
    timed("analyse text page 250") { PDDocument.load(big).use { val t = PdfTextRuns.analyse(it, 250); println("   words=${t.words.size}") } }
    timed("replace word on page 250") { PDDocument.load(big).use { doc -> val w = PdfTextRuns.analyse(doc, 250).words.first { it.text == "ipsum" }; println("   " + PdfTextReplace.replaceInPlace(doc, w, "ipsun")); doc.save(out("edit250")) } }
    timed("garbage merge") { PdfTools.merge(listOf(File(d + "garbage.pdf"), five), out("g1")) }
    timed("empty extract") { PdfTools.extractPages(File(d + "empty.pdf"), listOf(0), out("g2")) }
    timed("bad extract") { PdfTools.extractPages(five, listOf(99), out("g3")) }
    timed("bad rotate") { PdfTools.rotatePages(five, listOf(7), 90, out("g4")) }
    timed("bad insert") { PdfTools.insertFromPdf(five, File(d + "two.pdf"), listOf(5), 1, out("g5")) }
    timed("garbage form") { PdfForms.listFields(File(d + "garbage.pdf")) }
    timed("editor apply marks on 500") { PdfEditor.apply(big, out("marks"), mapOf(3 to List(300) { StrokeMark(listOf(NormPoint(0.1f, 0.1f), NormPoint(0.5f, 0.5f)), 0xFF000000.toInt(), 0.004f) } + FillRectMark(0.1f, 0.1f, 0.3f, 0.2f, 0xFFFFFFFF.toInt()))) }
}
