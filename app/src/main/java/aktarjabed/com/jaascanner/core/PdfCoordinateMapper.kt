package aktarjabed.com.jaascanner.core

/** Unrotated page box in PDF user-space units (1/72 inch). */
data class PageBox(val minX: Float, val minY: Float, val width: Float, val height: Float)

data class PdfPoint(val x: Float, val y: Float)

/**
 * Where to draw a stamp in PDF space: centre, size of the image as it should appear upright,
 * and the counter-clockwise rotation to apply so it looks upright on screen.
 */
data class StampPlacement(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angleDegrees: Float,
)

/**
 * Converts positions on the page as the user sees it (u to the right, v downwards, both 0..1)
 * into PDF coordinates (origin bottom-left, y up), taking the page's /Rotate value into account.
 * Pure Kotlin so every rotation can be unit-tested.
 */
object PdfCoordinateMapper {

    fun normalizeRotation(rotation: Int): Int {
        val wrapped = ((rotation % 360) + 360) % 360
        return (wrapped / 90) * 90
    }

    /** Width of the page as displayed (swapped for pages rotated by 90 or 270 degrees). */
    fun displayedWidth(box: PageBox, rotation: Int): Float =
        if (normalizeRotation(rotation) % 180 == 0) box.width else box.height

    fun point(box: PageBox, rotation: Int, u: Float, v: Float): PdfPoint =
        when (normalizeRotation(rotation)) {
            90 -> PdfPoint(box.minX + v * box.width, box.minY + u * box.height)
            180 -> PdfPoint(box.minX + box.width - u * box.width, box.minY + v * box.height)
            270 -> PdfPoint(box.minX + box.width - v * box.width, box.minY + box.height - u * box.height)
            else -> PdfPoint(box.minX + u * box.width, box.minY + box.height - v * box.height)
        }

    /**
     * @param widthFraction stamp width as a fraction of the displayed page width.
     * @param aspect bitmap width divided by bitmap height.
     * @param extraAngleDegrees additional counter-clockwise tilt as seen on screen (for diagonal watermarks).
     */
    fun stamp(
        box: PageBox,
        rotation: Int,
        centerU: Float,
        centerV: Float,
        widthFraction: Float,
        aspect: Float,
        extraAngleDegrees: Float = 0f,
    ): StampPlacement {
        val width = displayedWidth(box, rotation) * widthFraction
        val height = width / aspect
        val center = point(box, rotation, centerU, centerV)
        return StampPlacement(
            centerX = center.x,
            centerY = center.y,
            width = width,
            height = height,
            angleDegrees = normalizeRotation(rotation).toFloat() + extraAngleDegrees,
        )
    }
}
