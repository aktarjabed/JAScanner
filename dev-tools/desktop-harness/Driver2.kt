import aktarjabed.com.jaascanner.pdf.*
import java.io.File
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val d = "pdf/"
    val five = File(d + "five.pdf"); val two = File(d + "two.pdf"); val form = File(d + "form.pdf")
    fun out(n: String) = File(d + "o_" + n + ".pdf")
    PdfTools.merge(listOf(five, two), out("merge"))
    PdfTools.extractPages(five, listOf(0, 2, 4), out("extract"))
    PdfTools.deletePages(five, listOf(1, 3), out("delete"))
    PdfTools.rotatePages(five, listOf(0), 90, out("rotate"))
    var n = 0
    PdfTools.splitEvery(five, 2) { File(d + "o_splitevery${n++}.pdf") }
    var m = 0
    PdfTools.splitRanges(five, listOf(listOf(0, 1, 2), listOf(3, 4), listOf(4, 0))) { File(d + "o_range${m++}.pdf") }
    PdfTools.reorderPages(five, listOf(4, 3, 2, 1, 0), out("reorder"))
    PdfTools.insertBlankPages(five, 2, 2, PageSizeOption.A4, out("blank"))
    PdfTools.insertFromPdf(five, two, listOf(0, 1), 5, out("insert"))
    PdfTools.cropPages(five, listOf(0, 1), 10, out("crop"))
    PdfTools.protect(five, "secret", out("protect"))
    PdfTools.unlock(out("protect"), "secret", out("unlock"))
    try { PdfTools.unlock(out("protect"), "wrong", out("badunlock")) } catch (e: Throwable) { println("wrong password -> ${e.javaClass.simpleName}: ${e.message}") }
    try { PdfTools.deletePages(five, listOf(0,1,2,3,4), out("delall")) } catch (e: Throwable) { println("delete all -> ${e.message}") }
    try { PdfTools.reorderPages(five, listOf(0,1,2), out("badorder")) } catch (e: Throwable) { println("bad order -> ${e.message}") }
    try { PdfTools.extractPages(five, listOf(99), out("badextract")) } catch (e: Throwable) { println("bad extract -> ${e.javaClass.simpleName} ${e.message}") }
    try { PdfTools.insertFromPdf(five, two, listOf(5), 1, out("badins")) } catch (e: Throwable) { println("bad insert -> ${e.javaClass.simpleName} ${e.message}") }
    val fields = PdfForms.listFields(form)
    println("fields: " + fields.joinToString { "${it.name}:${it.kind}:${it.value}:${it.options}" })
    PdfForms.fill(form, out("filled"), mapOf("name" to "Kuku Barbhuiya", "agree" to "true", "sex" to "M", "dist" to "Karimganj"), false)
    PdfForms.fill(form, out("flat"), mapOf("name" to "Kuku Barbhuiya", "agree" to "true", "sex" to "F", "dist" to "Hailakandi"), true)
    println("after fill: " + PdfForms.listFields(out("filled")).joinToString { "${it.name}=${it.value}" })
    println("after flatten fields: " + PdfForms.listFields(out("flat")).size)
}
