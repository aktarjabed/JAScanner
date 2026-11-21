package aktarjabed.com.jaascanner.core

/**
 * Turns a grey image into pure black and white using a local (adaptive) threshold, which copes with
 * shadows and uneven lighting on photographed documents far better than one global cut-off.
 * Pure Kotlin so it is unit-tested. Based on Bradley and Roth's integral-image method.
 */
object Binarize {

    /** Ink must be at least this many grey levels darker than the local average; stops noise speckle in dark shadows. */
    const val MIN_DROP = 14

    /**
     * @param luminance width * height values, 0 (black) to 255 (white), row by row.
     * @param sensitivity how much darker than its surroundings a pixel must be to become black (0.05 to 0.3).
     * @return a same-sized array where true means black.
     */
    fun adaptive(
        luminance: IntArray,
        width: Int,
        height: Int,
        sensitivity: Float = 0.15f,
        minDrop: Int = MIN_DROP,
    ): BooleanArray {
        require(width > 0 && height > 0 && luminance.size == width * height) { "Size does not match." }
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            for (x in 1..width) {
                rowSum += luminance[(y - 1) * width + (x - 1)]
                integral[y * stride + x] = integral[(y - 1) * stride + x] + rowSum
            }
        }
        val half = (maxOf(width, height) / 16).coerceAtLeast(2)
        val black = BooleanArray(luminance.size)
        for (y in 0 until height) {
            val y0 = (y - half).coerceAtLeast(0)
            val y1 = (y + half).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val x0 = (x - half).coerceAtLeast(0)
                val x1 = (x + half).coerceAtMost(width - 1)
                val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * stride + (x1 + 1)] - integral[y0 * stride + (x1 + 1)] -
                    integral[(y1 + 1) * stride + x0] + integral[y0 * stride + x0]
                val value = luminance[y * width + x]
                // Darker than its surroundings by a margin, and by enough levels to be real ink rather than sensor noise.
                black[y * width + x] = value * count * 1.0 <= sum * (1.0 - sensitivity) && value * count + minDrop.toLong() * count <= sum
            }
        }
        return black
    }
}
