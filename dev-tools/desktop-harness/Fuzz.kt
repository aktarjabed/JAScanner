import aktarjabed.com.jaascanner.pdf.*
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlin.random.Random

/** For each word: swap one letter for another letter that exists on the page; check the page text changes exactly as intended. */
fun main(args: Array<String>) {
    val rnd = Random(42)
    var done = 0; var unsupported = 0; var wrong = 0; var crashed = 0
    val reasons = HashMap<String, Int>()
    for (path in args) {
        val file = File(path)
        val pages = PDDocument.load(file).use { it.numberOfPages }
        var fd = 0; var fu = 0
        for (page in 0 until pages) {
            val base = PDDocument.load(file).use { PdfTextRuns.analyse(it, page) }
            val letters = base.flat.filter { it.isLetterOrDigit() }.toSet().toList()
            if (letters.size < 3) continue
            val words = base.words.filter { it.text.length >= 2 }
            val picks = words.shuffled(rnd).take(40)
            for (w in picks) {
                val i = rnd.nextInt(w.text.length)
                if (!w.text[i].isLetterOrDigit()) continue
                val repl = letters.filter { it != w.text[i] }.random(rnd)
                val newText = w.text.replaceRange(i, i + 1, repl.toString())
                try {
                    PDDocument.load(file).use { doc ->
                        when (val r = PdfTextReplace.replaceInPlace(doc, w, newText)) {
                            PdfTextReplace.Outcome.Done -> {
                                val tmp = File.createTempFile("fuzz", ".pdf"); doc.save(tmp)
                                val after = PDDocument.load(tmp).use { PdfTextRuns.analyse(it, page) }
                                tmp.delete()
                                val expected = base.flat.replaceRange(w.flatStart + i, w.flatStart + i + 1, repl.toString())
                                if (after.flat == expected && after.words.size == base.words.size) { done++; fd++ }
                                else { wrong++; println("WRONG ${file.name} p$page '${w.text}'->'$newText'\n  got:      ${after.flat.take(200)}\n  expected: ${expected.take(200)}") }
                            }
                            is PdfTextReplace.Outcome.Unsupported -> { unsupported++; fu++; reasons.merge(r.reason.take(60), 1, Int::plus) }
                        }
                    }
                } catch (e: Throwable) { crashed++; println("CRASH ${file.name} p$page '${w.text}'->'$newText': $e") }
            }
        }
        println("${file.name}: in-place ok=$fd refused=$fu")
    }
    println("TOTAL ok=$done refused=$unsupported WRONG=$wrong CRASH=$crashed")
    reasons.entries.sortedByDescending { it.value }.forEach { println("  refused: ${it.value} x ${it.key}") }
}
