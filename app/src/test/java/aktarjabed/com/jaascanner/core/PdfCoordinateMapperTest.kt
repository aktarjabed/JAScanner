package aktarjabed.com.jaascanner.core

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The page box is 600 x 800 points with its origin at (0, 0). "Displayed" positions are
 * (u, v) measured from the top-left of the page as the user sees it, after /Rotate is applied.
 * Expected values come from rotating the corners of the page by hand.
 */
class PdfCoordinateMapperTest {

    private val box = PageBox(minX = 0f, minY = 0f, width = 600f, height = 800f)
    private val delta = 0.001f

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: PdfPoint) {
        assertEquals("x", expectedX, actual.x, delta)
        assertEquals("y", expectedY, actual.y, delta)
    }

    @Test
    fun rotation0CornersAndCentre() {
        assertPoint(0f, 800f, PdfCoordinateMapper.point(box, 0, 0f, 0f))
        assertPoint(600f, 800f, PdfCoordinateMapper.point(box, 0, 1f, 0f))
        assertPoint(0f, 0f, PdfCoordinateMapper.point(box, 0, 0f, 1f))
        assertPoint(600f, 0f, PdfCoordinateMapper.point(box, 0, 1f, 1f))
        assertPoint(300f, 400f, PdfCoordinateMapper.point(box, 0, 0.5f, 0.5f))
    }

    @Test
    fun rotation90TurnsThePageClockwise() {
        // Displayed top-left is the page's bottom-left; displayed top-right is the page's top-left.
        assertPoint(0f, 0f, PdfCoordinateMapper.point(box, 90, 0f, 0f))
        assertPoint(0f, 800f, PdfCoordinateMapper.point(box, 90, 1f, 0f))
        assertPoint(600f, 0f, PdfCoordinateMapper.point(box, 90, 0f, 1f))
        assertPoint(600f, 800f, PdfCoordinateMapper.point(box, 90, 1f, 1f))
    }

    @Test
    fun rotation180FlipsBothAxes() {
        assertPoint(600f, 0f, PdfCoordinateMapper.point(box, 180, 0f, 0f))
        assertPoint(0f, 0f, PdfCoordinateMapper.point(box, 180, 1f, 0f))
        assertPoint(600f, 800f, PdfCoordinateMapper.point(box, 180, 0f, 1f))
        assertPoint(0f, 800f, PdfCoordinateMapper.point(box, 180, 1f, 1f))
    }

    @Test
    fun rotation270TurnsThePageAnticlockwise() {
        // Displayed top-left is the page's top-right; displayed top-right is the page's bottom-right.
        assertPoint(600f, 800f, PdfCoordinateMapper.point(box, 270, 0f, 0f))
        assertPoint(600f, 0f, PdfCoordinateMapper.point(box, 270, 1f, 0f))
        assertPoint(0f, 800f, PdfCoordinateMapper.point(box, 270, 0f, 1f))
        assertPoint(0f, 0f, PdfCoordinateMapper.point(box, 270, 1f, 1f))
    }

    @Test
    fun centreMapsToCentreForEveryRotation() {
        for (rotation in listOf(0, 90, 180, 270)) {
            assertPoint(300f, 400f, PdfCoordinateMapper.point(box, rotation, 0.5f, 0.5f))
        }
    }

    @Test
    fun boxOriginOffsetIsRespected() {
        val offset = PageBox(minX = 10f, minY = 20f, width = 600f, height = 800f)
        assertPoint(10f, 820f, PdfCoordinateMapper.point(offset, 0, 0f, 0f))
        assertPoint(10f, 20f, PdfCoordinateMapper.point(offset, 90, 0f, 0f))
        assertPoint(610f, 820f, PdfCoordinateMapper.point(offset, 270, 0f, 0f))
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(0, PdfCoordinateMapper.normalizeRotation(0))
        assertEquals(0, PdfCoordinateMapper.normalizeRotation(360))
        assertEquals(90, PdfCoordinateMapper.normalizeRotation(450))
        assertEquals(270, PdfCoordinateMapper.normalizeRotation(-90))
        assertEquals(180, PdfCoordinateMapper.normalizeRotation(-180))
        assertEquals(0, PdfCoordinateMapper.normalizeRotation(45))
    }

    @Test
    fun displayedWidthSwapsForSidewaysPages() {
        assertEquals(600f, PdfCoordinateMapper.displayedWidth(box, 0), delta)
        assertEquals(800f, PdfCoordinateMapper.displayedWidth(box, 90), delta)
        assertEquals(600f, PdfCoordinateMapper.displayedWidth(box, 180), delta)
        assertEquals(800f, PdfCoordinateMapper.displayedWidth(box, 270), delta)
    }

    @Test
    fun stampFallsBackToASquareWhenTheAspectIsZeroOrInvalid() {
        for (aspect in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val stamp = PdfCoordinateMapper.stamp(box, 0, 0.5f, 0.5f, widthFraction = 0.5f, aspect = aspect)
            assertEquals("width for aspect $aspect", 300f, stamp.width, delta)
            assertEquals("height for aspect $aspect", 300f, stamp.height, delta)
            assertTrue("height must be finite for aspect $aspect", stamp.height.isFinite())
        }
    }

    @Test
    fun stampSizeAndAngleFollowThePage() {
        val upright = PdfCoordinateMapper.stamp(box, 0, 0.5f, 0.5f, widthFraction = 0.5f, aspect = 2f)
        assertEquals(300f, upright.width, delta)
        assertEquals(150f, upright.height, delta)
        assertEquals(0f, upright.angleDegrees, delta)

        // A page shown sideways: the stamp is as wide as the displayed (800 pt) width allows,
        // and is turned by the page rotation so it still reads upright on screen.
        val sideways = PdfCoordinateMapper.stamp(box, 90, 0.5f, 0.5f, widthFraction = 0.25f, aspect = 2f, extraAngleDegrees = 45f)
        assertEquals(200f, sideways.width, delta)
        assertEquals(100f, sideways.height, delta)
        assertEquals(135f, sideways.angleDegrees, delta)
        assertEquals(300f, sideways.centerX, delta)
        assertEquals(400f, sideways.centerY, delta)
    }
}
