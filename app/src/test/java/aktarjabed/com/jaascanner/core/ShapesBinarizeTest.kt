package aktarjabed.com.jaascanner.core

import aktarjabed.com.jaascanner.pdf.NormPoint
import aktarjabed.com.jaascanner.pdf.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapesBinarizeTest {

    @Test
    fun rectangleIsClosedAndOrderIndependent() {
        val a = Shapes.rectangle(NormPoint(0.6f, 0.7f), NormPoint(0.2f, 0.1f))
        assertEquals(5, a.size)
        assertEquals(a.first(), a.last())
        assertEquals(0.2f, a.minOf { it.u }, 1e-6f)
        assertEquals(0.7f, a.maxOf { it.v }, 1e-6f)
    }

    @Test
    fun ellipseStaysInsideItsBox() {
        val points = Shapes.ellipse(NormPoint(0.2f, 0.2f), NormPoint(0.6f, 0.4f))
        assertTrue(points.all { it.u in 0.199f..0.601f && it.v in 0.199f..0.401f })
        assertEquals(points.first().u, points.last().u, 1e-4f)
    }

    @Test
    fun arrowHasShaftAndTwoWingsThatTouchTheTip() {
        val tip = NormPoint(0.8f, 0.5f)
        val arrow = Shapes.arrow(NormPoint(0.2f, 0.5f), tip, aspect = 1.4f)
        assertEquals(5, arrow.size)
        assertEquals(tip, arrow[1])
        assertEquals(tip, arrow[3])
        // Wings sit behind the tip (smaller u, since the arrow points right) and on opposite sides.
        assertTrue(arrow[2].u < tip.u && arrow[4].u < tip.u)
        assertTrue((arrow[2].v - tip.v) * (arrow[4].v - tip.v) < 0f)
    }

    @Test
    fun zeroLengthArrowDoesNotBlowUp() {
        val p = NormPoint(0.5f, 0.5f)
        assertEquals(2, Shapes.arrow(p, p, 1.4f).size)
    }

    @Test
    fun darkTextOnShadedPaperBecomesBlackButShadeStaysWhite() {
        val w = 64
        val h = 64
        // Paper brightness fades from 230 (left) to 140 (right); a dark 3x3 mark sits on the dim side.
        val grey = IntArray(w * h) { i -> 230 - 90 * (i % w) / (w - 1) }
        for (y in 30..32) for (x in 50..52) grey[y * w + x] = 20
        val black = Binarize.adaptive(grey, w, h)
        assertTrue(black[31 * w + 51])
        assertFalse(black[10 * w + 60])
        assertFalse(black[10 * w + 5])
    }

    @Test
    fun uniformImageHasNoBlackPixels() {
        val black = Binarize.adaptive(IntArray(100) { 200 }, 10, 10)
        assertTrue(black.none { it })
    }

    @Test
    fun sensorNoiseInADarkAreaIsNotInk() {
        val random = java.util.Random(7)
        val width = 120
        val height = 120
        val pixels = IntArray(width * height) { 60 + random.nextInt(9) - 4 }
        val black = Binarize.adaptive(pixels, width, height)
        assertTrue("noise became ink: ${black.count { it }}", black.count { it } < width * height / 200)
    }

    @Test
    fun darkTextInAShadowedAreaIsStillFound() {
        val width = 120
        val height = 60
        val pixels = IntArray(width * height) { 70 }
        for (x in 30..90) pixels[30 * width + x] = 25
        val black = Binarize.adaptive(pixels, width, height)
        assertTrue(black[30 * width + 60])
        assertTrue(!black[10 * width + 10])
    }
}
