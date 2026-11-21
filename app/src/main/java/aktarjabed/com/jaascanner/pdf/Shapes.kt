package aktarjabed.com.jaascanner.pdf

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Outlines for the shape tools, as polylines in page-relative coordinates (u right, v down, 0..1).
 * Shapes are drawn with the same stroke code as the pen, so they need no extra PDF drawing code.
 * Pure Kotlin so it is unit-tested.
 */
object Shapes {

    fun line(a: NormPoint, b: NormPoint): List<NormPoint> = listOf(a, b)

    /** A closed rectangle: five points, the last equal to the first. */
    fun rectangle(a: NormPoint, b: NormPoint): List<NormPoint> {
        val left = min(a.u, b.u)
        val right = max(a.u, b.u)
        val top = min(a.v, b.v)
        val bottom = max(a.v, b.v)
        return listOf(
            NormPoint(left, top),
            NormPoint(right, top),
            NormPoint(right, bottom),
            NormPoint(left, bottom),
            NormPoint(left, top),
        )
    }

    /** A closed ellipse inscribed in the box spanned by [a] and [b]. */
    fun ellipse(a: NormPoint, b: NormPoint, steps: Int = 48): List<NormPoint> {
        val cu = (a.u + b.u) / 2f
        val cv = (a.v + b.v) / 2f
        val ru = abs(b.u - a.u) / 2f
        val rv = abs(b.v - a.v) / 2f
        return List(steps + 1) { i ->
            val angle = 2.0 * PI * i / steps
            NormPoint(cu + ru * cos(angle).toFloat(), cv + rv * sin(angle).toFloat())
        }
    }

    /**
     * A line from [a] to [b] with an arrow head at [b]: shaft, then both wings of the head.
     * @param aspect page height divided by page width, so the head looks symmetrical on screen.
     */
    fun arrow(a: NormPoint, b: NormPoint, aspect: Float, headFraction: Float = 0.035f): List<NormPoint> {
        val dx = b.u - a.u
        val dy = (b.v - a.v) * aspect
        val length = hypot(dx, dy)
        if (length < 1e-4f) return listOf(a, b)
        val backX = -dx / length
        val backY = -dy / length
        val head = min(headFraction, length / 2f)
        val angle = Math.toRadians(28.0)
        fun wing(sign: Double): NormPoint {
            val rx = (backX * cos(angle) - sign * backY * sin(angle)).toFloat()
            val ry = (sign * backX * sin(angle) + backY * cos(angle)).toFloat()
            return NormPoint(b.u + rx * head, b.v + ry * head / aspect)
        }
        return listOf(a, b, wing(1.0), b, wing(-1.0))
    }
}
