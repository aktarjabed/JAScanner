package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageListTest {

    private fun sources(list: List<PageEntry>) = list.map { it.source }

    @Test
    fun moveDragsOnePageToANewPlace() {
        val list = PageList.initial(5)
        assertEquals(listOf(1, 2, 3, 0, 4), sources(PageList.move(list, 0, 3)))
        assertEquals(listOf(4, 0, 1, 2, 3), sources(PageList.move(list, 4, 0)))
    }

    @Test
    fun moveClampsAndIgnoresBadIndexes() {
        val list = PageList.initial(3)
        assertEquals(listOf(1, 2, 0), sources(PageList.move(list, 0, 99)))
        assertEquals(list, PageList.move(list, 7, 0))
        assertEquals(list, PageList.move(list, 1, 1))
    }

    @Test
    fun shiftMovesABlockWithoutBreakingItsOrder() {
        val list = PageList.initial(6)
        assertEquals(listOf(1, 0, 2, 3, 4, 5), sources(PageList.shift(list, setOf(0), 1)))
        val later = PageList.shift(list, setOf(1, 2), 1)
        assertEquals(listOf(0, 3, 1, 2, 4, 5), sources(later))
        val earlier = PageList.shift(list, setOf(3, 4), -1)
        assertEquals(listOf(0, 1, 3, 4, 2, 5), sources(earlier))
    }

    @Test
    fun shiftStopsAtTheEnds() {
        val list = PageList.initial(3)
        assertEquals(list, PageList.shift(list, setOf(0), -1))
        assertEquals(list, PageList.shift(list, setOf(2), 1))
        assertEquals(list, PageList.shift(list, setOf(0, 1, 2), 1))
    }

    @Test
    fun moveToPutsTheBlockAtAPosition() {
        val list = PageList.initial(6)
        assertEquals(listOf(4, 5, 0, 1, 2, 3), sources(PageList.moveTo(list, setOf(4, 5), 1)))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), sources(PageList.moveTo(list, setOf(4, 5), 5)))
        assertEquals(listOf(1, 2, 0, 3, 4, 5), sources(PageList.moveTo(list, setOf(0), 3)))
        assertEquals(listOf(1, 2, 3, 4, 5, 0), sources(PageList.moveTo(list, setOf(0), 999)))
    }

    @Test
    fun rotateAccumulatesAndWraps() {
        val list = PageList.rotate(PageList.initial(2), setOf(1), 90)
        val twice = PageList.rotate(PageList.rotate(list, setOf(1), 90), setOf(1), 180)
        assertEquals(0, list[0].rotation)
        assertEquals(90, list[1].rotation)
        assertEquals(0, twice[1].rotation)
        assertEquals(270, PageList.rotate(PageList.initial(1), setOf(0), -90)[0].rotation)
    }

    @Test
    fun deleteNeverRemovesEveryPage() {
        val list = PageList.initial(3)
        assertEquals(listOf(0, 2), sources(PageList.delete(list, setOf(1))))
        assertEquals(list, PageList.delete(list, setOf(0, 1, 2)))
    }

    @Test
    fun duplicateInsertsACopyRightAfterTheOriginal() {
        val list = PageList.rotate(PageList.initial(3), setOf(1), 90)
        val copy = PageList.duplicate(list, setOf(1), PageList.nextId(list))
        assertEquals(listOf(0, 1, 1, 2), sources(copy))
        assertEquals(90, copy[2].rotation)
        assertEquals(4, copy.map { it.id }.toSet().size)
    }

    @Test
    fun unchangedIsDetected() {
        val list = PageList.initial(4)
        assertTrue(PageList.isUnchanged(list, 4))
        assertFalse(PageList.isUnchanged(PageList.move(list, 0, 1), 4))
        assertFalse(PageList.isUnchanged(PageList.rotate(list, setOf(0), 90), 4))
        assertFalse(PageList.isUnchanged(PageList.delete(list, setOf(0)), 4))
    }
}
