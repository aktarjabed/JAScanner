package aktarjabed.com.jaascanner.pdf

import aktarjabed.com.jaascanner.core.PageBox
import aktarjabed.com.jaascanner.core.PdfCoordinateMapper
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boxes must be in displayed space (u right, v down), because the editor draws them there and
 * PdfEditor maps them back with PdfCoordinateMapper.point(). These tests rebuild what PDFBox 2.0.27
 * reports for a glyph on each page rotation, then check both the box and the round trip back to
 * the page-space origin.
 */
class TextRunBuilderTest {

    private val pageWidth = 600f
    private val pageHeight = 800f
    private val box = PageBox(0f, 0f, pageWidth, pageHeight)
    private val delta = 0.0005f
    private val size = 20f
    private val advance = 12f

    /**
     * Simulates TextPosition for a glyph whose origin is (px, py) in unrotated page space and whose
     * text runs in [textDirection]. x/y/width follow the PDFBox 2.0.27 constructor exactly:
     * x = getXRot(pageRotation), y = pageHeight/pageWidth - getYLowerLeftRot(pageRotation), and
     * width = getWidthRot(pageRotation).
     */
    private fun sample(
        unicode: String,
        px: Float,
        py: Float,
        textDirection: Int,
        rotation: Int,
    ): GlyphSample {
        val endX = when (textDirection) {
            0 -> px + advance
            180 -> px - advance
            else -> px
        }
        val endY = when (textDirection) {
            90 -> py + advance
            270 -> py - advance
            else -> py
        }
        val x = when (rotation) {
            90 -> py
            180 -> pageWidth - px
            270 -> pageHeight - py
            else -> px
        }
        val y = when (rotation) {
            0 -> pageHeight - py
            90 -> px
            180 -> py
            270 -> pageWidth - px
            else -> pageHeight - py
        }
        val width = when (rotation) {
            90, 270 -> abs(endY - py)
            else -> abs(endX - px)
        }
        return GlyphSample(unicode, x, y, width, size, textDirection, "Test")
    }

    private fun build(glyphs: List<GlyphSample>, rotation: Int): PageText {
        val turned = rotation % 180 != 0
        val width = if (turned) pageHeight else pageWidth
        val height = if (turned) pageWidth else pageHeight
        return TextRunBuilder.build(glyphs, 0, rotation, width, height)
    }

    private fun assertBox(word: TextWord, u0: Float, u1: Float, v0: Float, v1: Float) {
        assertEquals("u0", u0, word.u0, delta)
        assertEquals("u1", u1, word.u1, delta)
        assertEquals("v0", v0, word.v0, delta)
        assertEquals("v1", v1, word.v1, delta)
    }

    /**
     * The displayed box, mapped back through PdfCoordinateMapper, must start at the first glyph's
     * page-space origin. This is the check that would fail if boxes were left in unrotated space.
     */
    private fun assertRoundTrip(word: TextWord, rotation: Int, expectedPx: Float, expectedPy: Float) {
        val point = PdfCoordinateMapper.point(box, rotation, word.u0, word.baselineV)
        assertEquals("page x", expectedPx, point.x, 0.01f)
        assertEquals("page y", expectedPy, point.y, 0.01f)
    }

    @Test
    fun displayDirectionTellsWhichTextIsUpright() {
        val expected = mapOf(
            0 to mapOf(0 to 0, 90 to 270, 180 to 180, 270 to 90),
            90 to mapOf(0 to 90, 90 to 0, 180 to 270, 270 to 180),
            180 to mapOf(0 to 180, 90 to 90, 180 to 0, 270 to 270),
            270 to mapOf(0 to 270, 90 to 180, 180 to 90, 270 to 0),
        )
        for ((rotation, row) in expected) {
            for ((direction, onScreen) in row) {
                assertEquals(
                    "rotation=$rotation direction=$direction",
                    onScreen,
                    TextRunBuilder.displayDirection(rotation, direction),
                )
                assertEquals(
                    onScreen == 0,
                    TextRunBuilder.isUprightOnScreen(rotation, direction),
                )
            }
        }
    }

    @Test
    fun directionsAreNormalised() {
        assertEquals(0, TextRunBuilder.normalizeDirection(0))
        assertEquals(0, TextRunBuilder.normalizeDirection(360))
        assertEquals(90, TextRunBuilder.normalizeDirection(450))
        assertEquals(270, TextRunBuilder.normalizeDirection(-90))
        assertEquals(180, TextRunBuilder.normalizeDirection(-180))
        assertEquals(270, TextRunBuilder.normalizeDirection(359))
    }

    @Test
    fun rotation0WordIsInDisplayedSpace() {
        val pageText = build(
            listOf(
                sample("H", px = 200f, py = 700f, textDirection = 0, rotation = 0),
                sample("i", px = 212f, py = 700f, textDirection = 0, rotation = 0),
            ),
            rotation = 0,
        )
        val word = pageText.words.single()
        assertEquals("Hi", word.text)
        // Displayed width 600, height 800.
        assertBox(word, u0 = 200f / 600f, u1 = 224f / 600f, v0 = 84f / 800f, v1 = 104.4f / 800f)
        assertEquals(100f / 800f, word.baselineV, delta)
        assertEquals(size / 600f, word.fontSizeFrac, delta)
        assertRoundTrip(word, rotation = 0, expectedPx = 200f, expectedPy = 700f)
    }

    @Test
    fun rotation90WordIsInDisplayedSpace() {
        // Text drawn at 90 degrees reads left-to-right once the page is shown clockwise.
        val pageText = build(
            listOf(
                sample("H", px = 100f, py = 200f, textDirection = 90, rotation = 90),
                sample("i", px = 100f, py = 212f, textDirection = 90, rotation = 90),
            ),
            rotation = 90,
        )
        val word = pageText.words.single()
        assertEquals("Hi", word.text)
        // Displayed width is the page height (800), displayed height is the page width (600).
        assertBox(word, u0 = 200f / 800f, u1 = 224f / 800f, v0 = 84f / 600f, v1 = 104.4f / 600f)
        assertEquals(100f / 600f, word.baselineV, delta)
        assertEquals(size / 800f, word.fontSizeFrac, delta)
        assertRoundTrip(word, rotation = 90, expectedPx = 100f, expectedPy = 200f)
    }

    @Test
    fun rotation180WordIsInDisplayedSpace() {
        val pageText = build(
            listOf(
                sample("H", px = 400f, py = 100f, textDirection = 180, rotation = 180),
                sample("i", px = 388f, py = 100f, textDirection = 180, rotation = 180),
            ),
            rotation = 180,
        )
        val word = pageText.words.single()
        assertEquals("Hi", word.text)
        assertBox(word, u0 = 200f / 600f, u1 = 224f / 600f, v0 = 84f / 800f, v1 = 104.4f / 800f)
        assertEquals(100f / 800f, word.baselineV, delta)
        assertRoundTrip(word, rotation = 180, expectedPx = 400f, expectedPy = 100f)
    }

    @Test
    fun rotation270WordIsInDisplayedSpace() {
        val pageText = build(
            listOf(
                sample("H", px = 500f, py = 600f, textDirection = 270, rotation = 270),
                sample("i", px = 500f, py = 588f, textDirection = 270, rotation = 270),
            ),
            rotation = 270,
        )
        val word = pageText.words.single()
        assertEquals("Hi", word.text)
        assertBox(word, u0 = 200f / 800f, u1 = 224f / 800f, v0 = 84f / 600f, v1 = 104.4f / 600f)
        assertEquals(100f / 600f, word.baselineV, delta)
        assertRoundTrip(word, rotation = 270, expectedPx = 500f, expectedPy = 600f)
    }

    @Test
    fun sidewaysTextIsKeptInFlatButIsNotEditable() {
        // Page rotated 90, text still drawn horizontally in page space: it appears vertical on
        // screen. It must not become a horizontal word, but its characters must stay in flat so
        // in-place replacement can still find them.
        val pageText = build(
            listOf(sample("X", px = 100f, py = 200f, textDirection = 0, rotation = 90)),
            rotation = 90,
        )
        assertTrue(pageText.words.isEmpty())
        assertEquals("X", pageText.flat)
    }

    @Test
    fun uprightWordsAreSeparatedFromSidewaysText() {
        val pageText = build(
            listOf(
                sample("A", px = 100f, py = 200f, textDirection = 90, rotation = 90),
                sample("B", px = 100f, py = 300f, textDirection = 0, rotation = 90),
            ),
            rotation = 90,
        )
        assertEquals("AB", pageText.flat)
        assertEquals(listOf("A"), pageText.words.map { it.text })
        // B is sideways on screen, so it must not be selectable.
        assertFalse(pageText.words.any { it.text == "B" })
    }

    @Test
    fun wordsOnOneLineAreSplitAtWideGaps() {
        val pageText = build(
            listOf(
                sample("a", px = 100f, py = 700f, textDirection = 0, rotation = 0),
                sample("b", px = 112f, py = 700f, textDirection = 0, rotation = 0),
                // Gap of 20 points, much wider than a space at 20 pt.
                sample("c", px = 144f, py = 700f, textDirection = 0, rotation = 0),
            ),
            rotation = 0,
        )
        assertEquals(listOf("ab", "c"), pageText.words.map { it.text })
    }

    @Test
    fun flatOffsetsAddressTheOriginalGlyphs() {
        val pageText = build(
            listOf(
                sample("a", px = 100f, py = 700f, textDirection = 0, rotation = 0),
                GlyphSample(" ", 112f, 700f, 6f, size, 0, "Test"),
                sample("b", px = 124f, py = 700f, textDirection = 0, rotation = 0),
            ),
            rotation = 0,
        )
        assertEquals("a b", pageText.flat)
        val first = pageText.words[0]
        val second = pageText.words[1]
        assertEquals("a", pageText.flat.substring(first.flatStart, first.flatEnd))
        assertEquals("b", pageText.flat.substring(second.flatStart, second.flatEnd))
    }
}
