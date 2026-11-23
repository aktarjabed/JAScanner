package aktarjabed.com.jaascanner.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera session's page list: the only part of the camera fallback that is not Android UI. */
class ScanSessionTest {

    private fun tempFile(name: String): File =
        File.createTempFile("scan-$name-", ".jpg").apply { deleteOnExit() }

    @Test
    fun refusesPagesOnceTheLimitIsReached() {
        val session = ScanSession(maxPages = 2)
        assertTrue(session.add(CapturedPage(tempFile("a"))))
        assertTrue(session.add(CapturedPage(tempFile("b"))))
        assertFalse(session.add(CapturedPage(tempFile("c"))))
        assertEquals(2, session.count)
        assertTrue(session.isFull)
    }

    @Test
    fun removeLastReturnsThePageAndEmptiesInOrder() {
        val session = ScanSession(maxPages = 5)
        val first = CapturedPage(tempFile("first"))
        val second = CapturedPage(tempFile("second"))
        session.add(first)
        session.add(second)

        assertEquals(second, session.removeLast())
        assertEquals(1, session.count)
        assertEquals(first, session.removeLast())
        assertNull(session.removeLast())
        assertTrue(session.isEmpty)
    }

    @Test
    fun snapshotIsACopy() {
        val session = ScanSession()
        val page = CapturedPage(tempFile("only"))
        session.add(page)
        val snapshot = session.snapshot()
        session.removeLast()
        assertEquals(listOf(page), snapshot)
        assertEquals(0, session.count)
    }

    @Test
    fun clearDeletesEveryCapturedFile() {
        val session = ScanSession()
        val first = tempFile("one")
        val second = tempFile("two")
        session.add(CapturedPage(first))
        session.add(CapturedPage(second))

        session.clear()

        assertTrue(session.isEmpty)
        assertFalse(first.exists())
        assertFalse(second.exists())
    }
}
