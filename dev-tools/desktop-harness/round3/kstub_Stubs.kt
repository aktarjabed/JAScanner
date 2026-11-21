package aktarjabed.com.jaascanner.pdf
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
enum class ImageFilter(val label: String) { Original("Original"), Enhanced("Enhanced"), Grayscale("Grayscale"), BlackWhite("Black and white") }
object ImageFilters { fun apply(b: Bitmap, f: ImageFilter): Bitmap = b }
object ImageDecoding { fun decode(c: Context, u: Uri, maxSide: Int): Bitmap = TODO(); fun flattenOnWhite(b: Bitmap): Bitmap = b }
enum class TextFace(val label: String, val family: String, val handwriting: Boolean) { Print("Print","a",false), Cursive("Cursive","c",true) }
object TextBitmaps { fun render(text: String, colorArgb: Int, textSizePx: Float = 96f, bold: Boolean = false, face: TextFace = TextFace.Print): Bitmap = TODO() }
data class PageSizePt(val width: Int, val height: Int)
class PdfPageRenderer { val pageCount = 0; suspend fun pageSize(i: Int): PageSizePt? = null; suspend fun render(i: Int, w: Int): Bitmap? = null; suspend fun release() {}
  companion object { suspend fun open(f: File): PdfPageRenderer? = null; fun pageCountOrZero(f: File): Int = 0 } }
