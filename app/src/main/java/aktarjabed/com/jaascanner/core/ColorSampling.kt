package aktarjabed.com.jaascanner.core

data class DetectedColors(val textArgb: Int, val backgroundArgb: Int, val foundInk: Boolean)

/** Estimates text and background colour from the rendered pixels around and inside a word. */
object ColorSampling {

    private const val MIN_CONTRAST = 60
    private const val OPAQUE = 0xFF000000.toInt()

    /**
     * @param pixels ARGB pixels of a crop, row by row.
     * The inner rectangle [left, right) x [top, bottom) is the word; everything else in the crop
     * is the surrounding background.
     */
    fun detect(pixels: IntArray, width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): DetectedColors {
        require(pixels.size >= width * height) { "pixel array too small" }
        data class Bucket(var count: Int = 0, var r: Long = 0, var g: Long = 0, var b: Long = 0)

        val buckets = HashMap<Int, Bucket>()
        fun inside(x: Int, y: Int) = x in left until right && y in top until bottom
        fun addTo(map: HashMap<Int, Bucket>, argb: Int) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = map.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.r += r
            bucket.g += g
            bucket.b += b
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!inside(x, y)) addTo(buckets, pixels[y * width + x])
            }
        }
        if (buckets.isEmpty()) {
            for (i in 0 until width * height) addTo(buckets, pixels[i])
        }
        val best = buckets.values.maxByOrNull { it.count } ?: return DetectedColors(OPAQUE, OPAQUE or 0xFFFFFF, false)
        val bgR = (best.r / best.count).toInt()
        val bgG = (best.g / best.count).toInt()
        val bgB = (best.b / best.count).toInt()
        val background = OPAQUE or (bgR shl 16) or (bgG shl 8) or bgB

        fun distance(argb: Int): Int =
            kotlin.math.abs(((argb shr 16) and 0xFF) - bgR) +
                kotlin.math.abs(((argb shr 8) and 0xFF) - bgG) +
                kotlin.math.abs((argb and 0xFF) - bgB)

        var maxDistance = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (x in 0 until width && y in 0 until height) maxDistance = maxOf(maxDistance, distance(pixels[y * width + x]))
            }
        }
        if (maxDistance < MIN_CONTRAST) {
            val luminance = (bgR * 299 + bgG * 587 + bgB * 114) / 1000
            return DetectedColors(if (luminance > 128) OPAQUE else 0xFFFFFFFF.toInt(), background, false)
        }
        val threshold = maxDistance * 3 / 4
        var count = 0
        var r = 0L
        var g = 0L
        var b = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                if (x !in 0 until width || y !in 0 until height) continue
                val argb = pixels[y * width + x]
                if (distance(argb) >= threshold) {
                    count++
                    r += (argb shr 16) and 0xFF
                    g += (argb shr 8) and 0xFF
                    b += argb and 0xFF
                }
            }
        }
        val text = OPAQUE or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
        return DetectedColors(text, background, true)
    }
}
