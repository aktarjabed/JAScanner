package aktarjabed.com.jaascanner.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import aktarjabed.com.jaascanner.core.Binarize
import aktarjabed.com.jaascanner.core.PixelFilters

enum class ImageFilter(val label: String) {
    Original("Original"),
    Enhanced("Enhanced"),
    Grayscale("Grayscale"),
    BlackWhite("Black and white"),
    Lighten("Lighten"),
    MagicColor("Magic colour"),
    Contrast("Strong contrast"),
}

/** Clean-up filters for pictures that become PDF pages. The input bitmap is never modified. */
object ImageFilters {

    fun apply(source: Bitmap, filter: ImageFilter): Bitmap = when (filter) {
        ImageFilter.Original -> source
        ImageFilter.Enhanced -> withMatrix(
            source,
            ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, -22f,
                    0f, 1.25f, 0f, 0f, -22f,
                    0f, 0f, 1.25f, 0f, -22f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        ImageFilter.Grayscale -> withMatrix(source, ColorMatrix().apply { setSaturation(0f) })
        ImageFilter.BlackWhite -> blackAndWhite(source)
        ImageFilter.Lighten -> viaPixels(source, PixelFilters::lighten)
        ImageFilter.MagicColor -> viaPixels(source, PixelFilters::magicColor)
        ImageFilter.Contrast -> viaPixels(source, PixelFilters::strongContrast)
    }

    private fun viaPixels(source: Bitmap, operation: (IntArray) -> IntArray): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val changed = operation(pixels)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(changed, 0, width, 0, 0, width, height)
        return result
    }

    private fun withMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun blackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
        }
        val black = Binarize.adaptive(luminance, width, height)
        for (i in pixels.indices) pixels[i] = if (black[i]) Color.BLACK else Color.WHITE
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
