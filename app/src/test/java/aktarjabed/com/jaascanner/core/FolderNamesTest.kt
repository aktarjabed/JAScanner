package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNamesTest {

    @Test
    fun whitespaceAndControlsCollapse() {
        assertEquals("Clients 2026", FolderNames.clean("  Clients\n\t2026  "))
        assertEquals("A B", FolderNames.clean("A\u0000\u0007B"))
    }

    @Test
    fun blankOrDotOnlyNamesAreRejected() {
        assertNull(FolderNames.clean(""))
        assertNull(FolderNames.clean("   "))
        assertNull(FolderNames.clean("\n\t"))
        assertNull(FolderNames.clean("."))
        assertNull(FolderNames.clean(".."))
    }

    @Test
    fun longNamesAreCappedWithoutSplittingAnEmoji() {
        val clean = FolderNames.clean("x" + "😀".repeat(100))
        assertTrue(clean != null)
        assertEquals(FolderNames.MAX_CODE_POINTS, clean!!.codePointCount(0, clean.length))
        assertFalse(clean.last().isHighSurrogate())
        assertTrue(clean.endsWith("😀"))
    }
}
