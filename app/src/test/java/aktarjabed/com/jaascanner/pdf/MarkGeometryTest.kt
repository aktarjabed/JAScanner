package aktarjabed.com.jaascanner.pdf

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Select tool finds, moves and resizes marks that were placed as fractions of the displayed
 * page. These tests cover the geometry directly: the page is 1000x2000, so aspect (height/width)
 * is 2 and one unit of v is twice as long on screen as one unit of u.
 */
class MarkGeometryTest {

    private val aspect = 2f
    private val delta = 0.0005f

    private fun word(u0: Float, v0: Float, u1: Float, v1: Float) = TextWord(
        text = "Rate",
        page = 0,
        u0 = u0,
        v0 = v0,
        u1 = u1,
        v1 = v1,
        baselineV = v1 - 0.005f,
        fontSizeFrac = 0.02f,
        fontName = "Helvetica",
        flatStart = 0,
        flatEnd = 4,
    )

    private fun assertBox(box: MarkBox, u0: Float, v0: Float, u1: Float, v1: Float) {
        assertEquals("u0", u0, box.u0, delta)
        assertEquals("v0", v0, box.v0, delta)
        assertEquals("u1", u1, box.u1, delta)
        assertEquals("v1", v1, box.v1, delta)
    }

    @Test
    fun strokeBoxIncludesHalfTheStrokeWidthOnBothAxes() {
        val mark = StrokeMark(
            points = listOf(NormPoint(0.2f, 0.2f), NormPoint(0.8f, 0.8f)),
            colorArgb = 0,
            widthFraction = 0.02f,
        )
        // Half the stroke is 0.01 of the page width; in v that is 0.01 / aspect.
        assertBox(MarkGeometry.box(mark, aspect), 0.19f, 0.195f, 0.81f, 0.805f)
    }

    @Test
    fun fillBoxNormalisesCornersDraggedInAnyDirection() {
        val mark = FillRectMark(0.8f, 0.9f, 0.2f, 0.3f, colorArgb = 0)
        assertBox(MarkGeometry.box(mark, aspect), 0.2f, 0.3f, 0.8f, 0.9f)
    }

    @Test
    fun noteBoxIsSquareOnScreen() {
        val mark = NoteMark(0.5f, 0.5f, "hello")
        val box = MarkGeometry.box(mark, aspect)
        // 0.02 of the page width on each side, which is 0.01 of the page height on a 1:2 page.
        assertBox(box, 0.48f, 0.49f, 0.52f, 0.51f)
    }

    @Test
    fun textEditBoxIsTheWordBox() {
        val mark = TextEditMark(word(0.1f, 0.2f, 0.3f, 0.23f), "Raxe", 0, 0)
        assertBox(MarkGeometry.box(mark, aspect), 0.1f, 0.2f, 0.3f, 0.23f)
    }

    @Test
    fun strokeHitTestsTheLineNotItsBoundingBox() {
        val mark = StrokeMark(
            points = listOf(NormPoint(0.1f, 0.1f), NormPoint(0.9f, 0.9f)),
            colorArgb = 0,
            widthFraction = 0.002f,
        )
        assertTrue(MarkGeometry.contains(mark, aspect, 0.5f, 0.5f))
        // Inside the bounding box but 0.3 of the page width away from the diagonal.
        assertFalse(MarkGeometry.contains(mark, aspect, 0.2f, 0.8f))
    }

    @Test
    fun hitToleranceIsMeasuredInPageWidth() {
        val mark = FillRectMark(0.4f, 0.4f, 0.6f, 0.6f, colorArgb = 0)
        // 0.02 of the page width beyond the edge still hits; 0.05 does not.
        assertTrue(MarkGeometry.contains(mark, aspect, 0.61f, 0.5f, tolerance = 0.02f))
        assertFalse(MarkGeometry.contains(mark, aspect, 0.65f, 0.5f, tolerance = 0.02f))
        // On the v axis the same tolerance is 0.01 of the page height.
        assertTrue(MarkGeometry.contains(mark, aspect, 0.5f, 0.605f, tolerance = 0.02f))
        assertFalse(MarkGeometry.contains(mark, aspect, 0.5f, 0.62f, tolerance = 0.02f))
    }

    @Test
    fun clampMoveStopsAtThePageEdge() {
        val mark = FillRectMark(0.8f, 0.4f, 0.9f, 0.6f, colorArgb = 0)
        val (du, dv) = MarkGeometry.clampMove(mark, aspect, 0.5f, -0.5f)
        assertEquals(0.1f, du, delta)
        assertEquals(-0.4f, dv, delta)
    }

    @Test
    fun movedShiftsAStrokeAndAnEditedWord() {
        val stroke = StrokeMark(listOf(NormPoint(0.1f, 0.2f)), colorArgb = 0, widthFraction = 0.01f)
        val movedStroke = MarkGeometry.moved(stroke, 0.05f, 0.03f) as StrokeMark
        assertEquals(0.15f, movedStroke.points[0].u, delta)
        assertEquals(0.23f, movedStroke.points[0].v, delta)

        val edit = TextEditMark(word(0.1f, 0.2f, 0.3f, 0.23f), "Raxe", 0, 0)
        val movedEdit = MarkGeometry.moved(edit, 0.05f, 0.03f) as TextEditMark
        assertEquals(0.15f, movedEdit.word.u0, delta)
        assertEquals(0.35f, movedEdit.word.u1, delta)
        assertEquals(0.26f, movedEdit.word.v1, delta)
        // A moved edit can no longer change the page's own text in place; the original word is
        // kept so the old text can be covered where it actually is.
        assertTrue(movedEdit.layoutChanged)
        assertEquals(0.1f, movedEdit.sourceWord.u0, delta)
    }

    @Test
    fun scalingKeepsTheAnchorFixedAndScalesFontSize() {
        val edit = TextEditMark(word(0.2f, 0.2f, 0.4f, 0.25f), "Raxe", 0, 0)
        val scaled = MarkGeometry.scaled(edit, 0.2f, 0.2f, 2f) as TextEditMark
        assertEquals(0.2f, scaled.word.u0, delta)
        assertEquals(0.6f, scaled.word.u1, delta)
        assertEquals(0.3f, scaled.word.v1, delta)
        assertEquals(0.04f, scaled.word.fontSizeFrac, delta)
        assertTrue(scaled.layoutChanged)
        assertEquals(0.2f, scaled.sourceWord.u0, delta)
    }

    @Test
    fun freeFillResizeFollowsTheFinger() {
        val mark = FillRectMark(0.2f, 0.2f, 0.4f, 0.4f, colorArgb = 0)
        val box = MarkGeometry.box(mark, aspect)
        val resized = MarkGeometry.resized(mark, aspect, box, 0.7f, 0.8f) as FillRectMark
        assertEquals(0.2f, resized.u0, delta)
        assertEquals(0.7f, resized.u1, delta)
        assertEquals(0.8f, resized.v1, delta)
    }

    @Test
    fun uniformResizeFollowsTheDiagonalAndStaysOnThePage() {
        val edit = TextEditMark(word(0.2f, 0.2f, 0.4f, 0.3f), "Raxe", 0, 0)
        val box = MarkGeometry.box(edit, aspect)
        // Finger at the old corner: no change.
        val same = MarkGeometry.resized(edit, aspect, box, 0.4f, 0.3f) as TextEditMark
        assertEquals(0.4f, same.word.u1, delta)
        // Dragging past the page edge is clamped so the box stays inside.
        val clamped = MarkGeometry.resized(edit, aspect, box, 2f, 2f) as TextEditMark
        assertTrue(clamped.word.u1 <= 1f + delta)
        assertTrue(clamped.word.v1 <= 1f + delta)
        // Shrinking to a point stops at the minimum scale, not at zero.
        val tiny = MarkGeometry.resized(edit, aspect, box, 0.2f, 0.2f) as TextEditMark
        assertTrue(tiny.word.u1 - tiny.word.u0 > 0f)
        assertTrue(abs(tiny.word.v1 - tiny.word.v0) > 0f)
    }

    @Test
    fun notesCannotBeResized() {
        val note = NoteMark(0.5f, 0.5f, "hello")
        assertFalse(MarkGeometry.canResize(note))
        val box = MarkGeometry.box(note, aspect)
        assertTrue(MarkGeometry.resized(note, aspect, box, 0.9f, 0.9f) === note)
    }

    @Test
    fun handleIsAHitTargetAroundTheBottomRightCorner() {
        val box = MarkBox(0.2f, 0.2f, 0.6f, 0.5f)
        assertTrue(MarkGeometry.onHandle(box, aspect, 0.6f, 0.5f))
        assertTrue(MarkGeometry.onHandle(box, aspect, 0.63f, 0.5f, radius = 0.05f))
        assertFalse(MarkGeometry.onHandle(box, aspect, 0.6f, 0.6f))
    }
}
