package aktarjabed.com.jaascanner.core

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PageSelectionTest {

    private fun pages(text: String, count: Int = 10): List<Int> {
        val result = PageSelection.parse(text, count)
        assertTrue("expected Ok for \"$text\" but got $result", result is PageSelectionResult.Ok)
        return (result as PageSelectionResult.Ok).pages
    }

    private fun assertError(text: String, count: Int = 10) {
        val result = PageSelection.parse(text, count)
        assertTrue("expected Error for \"$text\" but got $result", result is PageSelectionResult.Error)
    }

    @Test
    fun singlePagesBecomeZeroBasedIndices() {
        assertEquals(listOf(0, 2), pages("1, 3"))
    }

    @Test
    fun rangesAreExpanded() {
        assertEquals(listOf(1, 2, 3), pages("2-4"))
    }

    @Test
    fun openEndedRangeRunsToLastPage() {
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), pages("1-3, 5, 8-"))
    }

    @Test
    fun resultIsSortedAndWithoutDuplicates() {
        assertEquals(listOf(0, 1, 2, 4), pages("5,1,5,2-3"))
    }

    @Test
    fun whitespaceAndTrailingCommaAreIgnored() {
        assertEquals(listOf(1, 2, 3), pages("  2 - 4 "))
        assertEquals(listOf(0), pages("1,"))
    }

    @Test
    fun emptyInputIsAnError() {
        assertError("")
        assertError("  , ,")
    }

    @Test
    fun pagesOutsideTheDocumentAreErrors() {
        assertError("0")
        assertError("11")
        assertError("1-11")
    }

    @Test
    fun backwardsRangeIsAnError() {
        assertError("3-1")
        assertError("8-", count = 5)
    }

    @Test
    fun garbageIsAnError() {
        assertError("a")
        assertError("1-2-3")
        assertError("-3")
        assertError("1.5")
    }

    @Test
    fun errorMessagesAreHumanReadable() {
        val result = PageSelection.parse("11", 10) as PageSelectionResult.Error
        assertEquals("Page 11 is outside 1-10.", result.message)
    }
}
