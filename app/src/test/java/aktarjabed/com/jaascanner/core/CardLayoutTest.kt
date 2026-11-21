package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {

    private val aspect = 1.586f

    @Test
    fun twoCardsKeepRealWidthAndDoNotOverlap() {
        val spots = CardLayout.stack(listOf(aspect, aspect), 595f, 842f)
        assertEquals(2, spots.size)
        assertEquals(CardLayout.CARD_WIDTH_PT, spots[0].width, 0.01f)
        assertTrue(spots[0].y >= spots[1].y + spots[1].height)
        assertEquals((595f - spots[0].width) / 2f, spots[0].x, 0.01f)
    }

    @Test
    fun manySidesShrinkToFitInsideTheMargins() {
        val spots = CardLayout.stack(List(12) { aspect }, 595f, 842f)
        assertTrue(spots.all { it.y >= 36f - 0.01f && it.y + it.height <= 842f - 36f + 0.01f })
        assertTrue(spots.all { it.width < CardLayout.CARD_WIDTH_PT })
    }

    @Test
    fun emptyInputGivesNoSpots() {
        assertTrue(CardLayout.stack(emptyList(), 595f, 842f).isEmpty())
    }

    @Test
    fun pageSmallerThanTheMarginsDoesNotGoNegative() {
        // A degenerate/malformed page smaller than twice the default margin used to make usableW
        // and usableH negative, producing inverted rectangles. It must now clamp instead.
        val spots = CardLayout.stack(listOf(aspect), 40f, 40f)
        assertEquals(1, spots.size)
        assertTrue(spots[0].width > 0f)
        assertTrue(spots[0].height > 0f)
    }
}
