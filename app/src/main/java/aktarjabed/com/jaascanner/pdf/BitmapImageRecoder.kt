package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlin.math.max
import kotlin.math.roundToInt

/** The phone's way of re-compressing a picture inside a PDF: decode, shrink if too big, save as JPEG. */
class BitmapImageRecoder : ImageRecoder {

    override fun recode(document: PDDocument, image: PDImageXObject, maxSidePx: Int, jpegQuality: Float): PDImageXObject? {
        val decoded: Bitmap = image.image ?: return null
        var scaled: Bitmap? = null
        try {
            val longest = max(decoded.width, decoded.height)
            val candidate = if (longest > maxSidePx) {
                val factor = maxSidePx.toFloat() / longest
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * factor).roundToInt().coerceAtLeast(1),
                    (decoded.height * factor).roundToInt().coerceAtLeast(1),
                    true,
                ).also { scaled = it }
            } else {
                decoded
            }
            return JPEGFactory.createFromImage(document, candidate, jpegQuality)
        } finally {
            scaled?.recycle()
            decoded.recycle()
        }
    }
}
