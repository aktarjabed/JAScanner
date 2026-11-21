package aktarjabed.com.jaascanner.core

import kotlin.math.pow

/**
 * Colour clean-ups on ARGB pixel arrays, the same idea as the "Lighten", "Colour" and "Dark"
 * modes of scanner apps. Pure Kotlin so they can be unit-tested. Alpha is set to opaque.
 */
object PixelFilters {

    private const val OPAQUE = 0xFF000000.toInt()
    private const val PAPER_PERCENTILE = 0.92f
    private const val MAX_GAIN = 2.2f
    private const val SAMPLE_STEP = 7

    /** Brightens mid-tones and whitens the paper while keeping real black text dark. */
    fun lighten(pixels: IntArray): IntArray {
        val table = IntArray(256) { v -> ((255.0 * (v / 255.0).pow(0.62) * 1.04).toInt()).coerceIn(0, 255) }
        return map(pixels) { table[it] }
    }

    /** Deeper text and whiter paper: an S-curve on every channel. */
    fun strongContrast(pixels: IntArray): IntArray {
        val table = IntArray(256) { v ->
            val x = v / 255.0
            val y = if (x < 0.5) 2.0 * x * x else 1.0 - 2.0 * (1.0 - x) * (1.0 - x)
            (y * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
        return map(pixels) { table[it] }
    }

    /**
     * Colour mode: finds the paper colour of each channel (a high percentile, so yellow or blue
     * tinted paper is detected), scales it to white, then applies a gentle contrast curve. Photos
     * with no paper in them are touched very little because the gain is capped.
     */
    fun magicColor(pixels: IntArray): IntArray {
        if (pixels.isEmpty()) return pixels
        val histograms = Array(3) { IntArray(256) }
        var counted = 0
        var index = 0
        while (index < pixels.size) {
            val c = pixels[index]
            histograms[0][(c shr 16) and 0xFF]++
            histograms[1][(c shr 8) and 0xFF]++
            histograms[2][c and 0xFF]++
            counted++
            index += SAMPLE_STEP
        }
        val limit = (counted * PAPER_PERCENTILE).toInt()
        val gains = FloatArray(3) { channel ->
            var running = 0
            var level = 255
            for (v in 0..255) {
                running += histograms[channel][v]
                if (running >= limit) {
                    level = v
                    break
                }
            }
            (255f / level.coerceAtLeast(1)).coerceIn(1f, MAX_GAIN)
        }
        val curve = IntArray(256) { v ->
            val x = v / 255.0
            val y = if (x < 0.5) 2.0 * x * x else 1.0 - 2.0 * (1.0 - x) * (1.0 - x)
            ((0.5 * x + 0.5 * y) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
        return IntArray(pixels.size) { i ->
            val c = pixels[i]
            val r = curve[((((c shr 16) and 0xFF) * gains[0]).toInt()).coerceIn(0, 255)]
            val g = curve[((((c shr 8) and 0xFF) * gains[1]).toInt()).coerceIn(0, 255)]
            val b = curve[(((c and 0xFF) * gains[2]).toInt()).coerceIn(0, 255)]
            OPAQUE or (r shl 16) or (g shl 8) or b
        }
    }

    private inline fun map(pixels: IntArray, channel: (Int) -> Int): IntArray = IntArray(pixels.size) { i ->
        val c = pixels[i]
        OPAQUE or
            (channel((c shr 16) and 0xFF) shl 16) or
            (channel((c shr 8) and 0xFF) shl 8) or
            channel(c and 0xFF)
    }
}
