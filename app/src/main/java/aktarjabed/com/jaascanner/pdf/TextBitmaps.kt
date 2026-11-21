package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/**
 * Typefaces offered for typed text. The handwriting-style faces come from the phone's own fonts
 * (Android ships a cursive and a casual handwriting family); a phone without them shows the normal font.
 */
enum class TextFace(val label: String, val family: String, val handwriting: Boolean) {
    Print("Print", "sans-serif", false),
    Serif("Serif", "serif", false),
    Typewriter("Typewriter", "monospace", false),
    Cursive("Cursive", "cursive", true),
    Casual("Casual", "casual", true),
    ;

    fun typeface(bold: Boolean = false): Typeface =
        Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** Renders text to a transparent bitmap so it can be stamped onto a PDF page. */
object TextBitmaps {

    private const val PADDING_PX = 8
    private const val MAX_CHARS = 200

    fun render(
        text: String,
        colorArgb: Int,
        textSizePx: Float = 96f,
        bold: Boolean = false,
        face: TextFace = TextFace.Print,
    ): Bitmap {
        val clean = text.take(MAX_CHARS).ifBlank { " " }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = textSizePx
            typeface = face.typeface(bold)
        }
        val widest = clean.lines().maxOf { paint.measureText(it) }
        val contentWidth = ceil(widest).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(clean, 0, clean.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val bitmap = Bitmap.createBitmap(
            contentWidth + 2 * PADDING_PX,
            layout.height.coerceAtLeast(1) + 2 * PADDING_PX,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.translate(PADDING_PX.toFloat(), PADDING_PX.toFloat())
        layout.draw(canvas)
        return bitmap
    }
}
