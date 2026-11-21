package aktarjabed.com.jaascanner.core

import aktarjabed.com.jaascanner.data.db.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGroupsTest {

    @Test
    fun groupsAreSplitOnSemicolons() {
        val result = PageGroups.parse("1-3; 4-6 ;7", 10) as PageGroupsResult.Ok
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6)), result.groups)
    }

    @Test
    fun outOfRangeGroupIsReported() {
        assertTrue(PageGroups.parse("1-3; 9-12", 10) is PageGroupsResult.Error)
    }

    @Test
    fun emptyInputIsReported() {
        assertTrue(PageGroups.parse("  ;  ", 5) is PageGroupsResult.Error)
    }

    @Test
    fun pageOrderMustBeAFullPermutation() {
        val ok = PageOrder.parse("3, 1, 2", 3) as PageSelectionResult.Ok
        assertEquals(listOf(2, 0, 1), ok.pages)
        assertTrue(PageOrder.parse("1, 2", 3) is PageSelectionResult.Error)
        assertTrue(PageOrder.parse("1, 1, 2", 3) is PageSelectionResult.Error)
        assertTrue(PageOrder.parse("1, 2, 4", 3) is PageSelectionResult.Error)
        assertTrue(PageOrder.parse("1, x, 3", 3) is PageSelectionResult.Error)
    }

    @Test
    fun folderPathShowsAncestors() {
        val folders = listOf(
            FolderEntity(1, "Clients"),
            FolderEntity(2, "2026", parentId = 1),
            FolderEntity(3, "March", parentId = 2),
        )
        assertEquals("Clients / 2026 / March", FolderPath.label(folders, 3))
        assertEquals(setOf(1L, 2L, 3L), FolderPath.descendants(folders, 1))
    }
}
