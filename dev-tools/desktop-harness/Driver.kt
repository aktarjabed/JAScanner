import aktarjabed.com.jaascanner.pdf.*
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File

fun main(args: Array<String>) {
    val file = File(args[0]); val page = args[1].toInt(); val old = args[2]; val new = args[3]; val out = File(args[4])
    PDDocument.load(file).use { doc ->
        val text = PdfTextRuns.analyse(doc, page)
        println("words: " + text.words.joinToString(" | ") { "${it.text}@${"%.3f".format(it.u0)},${"%.3f".format(it.v0)}" })
        val w = text.words.firstOrNull { it.text == old }
        if (w == null) { println("NOWORD $old"); return }
        println("font=${w.fontName} size=${"%.4f".format(w.fontSizeFrac)}")
        val r = PdfTextReplace.replaceInPlace(doc, w, new)
        println("RESULT $r")
        doc.save(out)
    }
}
