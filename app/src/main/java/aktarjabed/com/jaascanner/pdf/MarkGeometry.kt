package aktarjabed.com.jaascanner.pdf

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * An axis-aligned box on the page as displayed, in (u, v) fractions: u to the right, v downwards.
 * Used to draw the selection frame and to hit-test taps.
 */
data class MarkBox(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
) {
    val width: Float get() = u1 - u0
    val height: Float get() = v1 - v0

    fun inflated(du: Float, dv: Float) = MarkBox(u0 - du, v0 - dv, u1 + du, v1 + dv)

    /**
     * Whether ([u], [v]) is inside this box, grown by [tolerance] page-width fractions.
     * [aspect] is page height divided by page width, so the tolerance is the same on both axes.
     */
    fun contains(u: Float, v: Float, tolerance: Float, aspect: Float): Boolean {
        val tolV = tolerance / aspect
        return u >= u0 - tolerance && u <= u1 + tolerance && v >= v0 - tolV && v <= v1 + tolV
    }
}

/**
 * Where each kind of editor mark sits on the page, and how it moves or resizes.
 *
 * The user places marks as fractions of the displayed page. Moving and resizing them is pure
 * arithmetic on those fractions, so it is unit-tested without Android or PDFBox. [aspect] is the
 * page height divided by its width; it is needed because u and v are fractions of different
 * lengths (a square stamp is not a square in (u, v) space).
 */
object MarkGeometry {

    /** How close a tap must be to a mark, as a fraction of the page width. */
    const val HIT_TOLERANCE = 0.022f

    /** Grab radius of the resize handle, as a fraction of the page width. */
    const val HANDLE_RADIUS = 0.05f

    /** Smallest factor a mark can be scaled to with the resize handle. */
    private const val MIN_SCALE = 0.05f

    /** Smallest side of a freely resized fill box, as a fraction of the page width. */
    private const val MIN_FILL_SIZE = 0.02f

    /** Half the on-screen note icon, as a fraction of the page width. */
    private const val NOTE_HALF = 0.02f

    fun box(mark: Mark, aspect: Float): MarkBox = when (mark) {
        is StrokeMark -> {
            val half = mark.widthFraction / 2f
            boxOfPoints(mark.points).inflated(half, half / aspect)
        }
        is FillRectMark -> MarkBox(
            min(mark.u0, mark.u1),
            min(mark.v0, mark.v1),
            max(mark.u0, mark.u1),
            max(mark.v0, mark.v1),
        )
        is StampMark -> {
            val halfU = mark.widthFraction / 2f
            val bitmapAspect = if (mark.bitmap.height > 0 && mark.bitmap.width > 0) {
                mark.bitmap.height.toFloat() / mark.bitmap.width
            } else {
                1f
            }
            val halfV = halfU * bitmapAspect / aspect
            MarkBox(mark.centerU - halfU, mark.centerV - halfV, mark.centerU + halfU, mark.centerV + halfV)
        }
        is TextEditMark -> MarkBox(mark.word.u0, mark.word.v0, mark.word.u1, mark.word.v1)
        is NoteMark -> MarkBox(
            mark.u - NOTE_HALF,
            mark.v - NOTE_HALF / aspect,
            mark.u + NOTE_HALF,
            mark.v + NOTE_HALF / aspect,
        )
    }

    /** Whether a tap at ([u], [v]) is on [mark]. Strokes are tested against their real path. */
    fun contains(
        mark: Mark,
        aspect: Float,
        u: Float,
        v: Float,
        tolerance: Float = HIT_TOLERANCE,
    ): Boolean = when (mark) {
        is StrokeMark -> nearStroke(mark, aspect, u, v, tolerance + mark.widthFraction / 2f)
        else -> box(mark, aspect).contains(u, v, tolerance, aspect)
    }

    /** Whether ([u], [v]) is on the bottom-right resize handle of [box]. */
    fun onHandle(box: MarkBox, aspect: Float, u: Float, v: Float, radius: Float = HANDLE_RADIUS): Boolean {
        val dx = u - box.u1
        val dy = (v - box.v1) * aspect
        return hypot(dx, dy) <= radius
    }

    /** Notes have no meaningful size, so they move but do not resize. */
    fun canResize(mark: Mark): Boolean = mark !is NoteMark

    /**
     * Moves [mark] by ([du], [dv]) but never lets it leave the page. Returns the allowed delta.
     * A mark that is already partly off the page is only moved as far as the page allows.
     */
    fun clampMove(mark: Mark, aspect: Float, du: Float, dv: Float): Pair<Float, Float> {
        val box = box(mark, aspect)
        return clamp(du, -box.u0, 1f - box.u1) to clamp(dv, -box.v0, 1f - box.v1)
    }

    fun moved(mark: Mark, du: Float, dv: Float): Mark = when (mark) {
        is StrokeMark -> mark.copy(points = mark.points.map { NormPoint(it.u + du, it.v + dv) })
        is FillRectMark -> mark.copy(
            u0 = mark.u0 + du,
            v0 = mark.v0 + dv,
            u1 = mark.u1 + du,
            v1 = mark.v1 + dv,
        )
        is StampMark -> StampMark(
            bitmap = mark.bitmap,
            centerU = mark.centerU + du,
            centerV = mark.centerV + dv,
            widthFraction = mark.widthFraction,
            extraAngleDegrees = mark.extraAngleDegrees,
            opacity = mark.opacity,
        )
        is TextEditMark -> TextEditMark(
            word = moveWord(mark.word, du, dv),
            newText = mark.newText,
            textArgb = mark.textArgb,
            backgroundArgb = mark.backgroundArgb,
            face = mark.face,
            sourceWord = mark.sourceWord,
            layoutChanged = true,
        )
        is NoteMark -> NoteMark(mark.u + du, mark.v + dv, mark.text)
    }

    /**
     * Resizes [mark] so that the bottom-right corner of [box] follows ([u], [v]).
     * Fill boxes resize freely on both axes; everything else keeps its proportions.
     */
    fun resized(mark: Mark, aspect: Float, box: MarkBox, u: Float, v: Float): Mark {
        if (!canResize(mark)) return mark
        if (mark is FillRectMark) {
            val minV = MIN_FILL_SIZE / aspect
            val lowerU = (box.u0 + MIN_FILL_SIZE).coerceAtMost(1f)
            val lowerV = (box.v0 + minV).coerceAtMost(1f)
            val u1 = u.coerceIn(lowerU, 1f)
            val v1 = v.coerceIn(lowerV, 1f)
            return mark.copy(u0 = box.u0, v0 = box.v0, u1 = u1, v1 = v1)
        }

        val anchorU = box.u0
        val anchorV = box.v0
        val spanX = box.width.coerceAtLeast(1e-4f)
        val spanY = (box.height * aspect).coerceAtLeast(1e-4f)
        val pointerX = u - anchorU
        val pointerY = (v - anchorV) * aspect
        // Project the finger onto the box diagonal, so dragging along either axis feels natural.
        val factor = (pointerX * spanX + pointerY * spanY) / (spanX * spanX + spanY * spanY)

        val maxX = if (box.u1 > anchorU) (1f - anchorU) / (box.u1 - anchorU) else 1f
        val maxY = if (box.v1 > anchorV) (1f - anchorV) / (box.v1 - anchorV) else 1f
        val maxScale = min(maxX, maxY).coerceAtLeast(MIN_SCALE)
        return scaled(mark, anchorU, anchorV, factor.coerceIn(MIN_SCALE, maxScale))
    }

    /** Scales [mark] about ([anchorU], [anchorV]) by [factor]. */
    fun scaled(mark: Mark, anchorU: Float, anchorV: Float, factor: Float): Mark = when (mark) {
        is StrokeMark -> mark.copy(
            points = mark.points.map {
                NormPoint(
                    anchorU + (it.u - anchorU) * factor,
                    anchorV + (it.v - anchorV) * factor,
                )
            },
        )
        is FillRectMark -> mark.copy(
            u0 = anchorU + (mark.u0 - anchorU) * factor,
            v0 = anchorV + (mark.v0 - anchorV) * factor,
            u1 = anchorU + (mark.u1 - anchorU) * factor,
            v1 = anchorV + (mark.v1 - anchorV) * factor,
        )
        is StampMark -> StampMark(
            bitmap = mark.bitmap,
            centerU = anchorU + (mark.centerU - anchorU) * factor,
            centerV = anchorV + (mark.centerV - anchorV) * factor,
            widthFraction = (mark.widthFraction * factor).coerceIn(0.03f, 0.95f),
            extraAngleDegrees = mark.extraAngleDegrees,
            opacity = mark.opacity,
        )
        is TextEditMark -> {
            val word = mark.word
            TextEditMark(
                word = word.copy(
                    u0 = anchorU + (word.u0 - anchorU) * factor,
                    v0 = anchorV + (word.v0 - anchorV) * factor,
                    u1 = anchorU + (word.u1 - anchorU) * factor,
                    v1 = anchorV + (word.v1 - anchorV) * factor,
                    baselineV = anchorV + (word.baselineV - anchorV) * factor,
                    fontSizeFrac = word.fontSizeFrac * factor,
                ),
                newText = mark.newText,
                textArgb = mark.textArgb,
                backgroundArgb = mark.backgroundArgb,
                face = mark.face,
                sourceWord = mark.sourceWord,
                layoutChanged = true,
            )
        }
        is NoteMark -> mark
    }

    private fun moveWord(word: TextWord, du: Float, dv: Float): TextWord = word.copy(
        u0 = word.u0 + du,
        v0 = word.v0 + dv,
        u1 = word.u1 + du,
        v1 = word.v1 + dv,
        baselineV = word.baselineV + dv,
    )

    private fun boxOfPoints(points: List<NormPoint>): MarkBox {
        if (points.isEmpty()) return MarkBox(0f, 0f, 0f, 0f)
        var left = points[0].u
        var right = points[0].u
        var top = points[0].v
        var bottom = points[0].v
        for (point in points) {
            left = min(left, point.u)
            right = max(right, point.u)
            top = min(top, point.v)
            bottom = max(bottom, point.v)
        }
        return MarkBox(left, top, right, bottom)
    }

    private fun nearStroke(
        mark: StrokeMark,
        aspect: Float,
        u: Float,
        v: Float,
        tolerance: Float,
    ): Boolean {
        val points = mark.points
        if (points.isEmpty()) return false
        val x = u
        val y = v * aspect
        if (points.size == 1) {
            return hypot(x - points[0].u, y - points[0].v * aspect) <= tolerance
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (distanceToSegment(x, y, a.u, a.v * aspect, b.u, b.v * aspect) <= tolerance) return true
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 1e-9f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Coerces [value] into [lower]..[upper]; when the range is inverted, returns its middle. */
    private fun clamp(value: Float, lower: Float, upper: Float): Float =
        if (lower <= upper) value.coerceIn(lower, upper) else (lower + upper) / 2f
}
