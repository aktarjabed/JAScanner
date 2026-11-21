package aktarjabed.com.jaascanner.core

import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FormattersTest {

    @Test
    fun fileSizeUsesReadableUnits() {
        assertEquals("0 B", Formatters.fileSize(0))
        assertEquals("512 B", Formatters.fileSize(512))
        assertEquals("1.5 KB", Formatters.fileSize(1_536))
        assertEquals("1.0 MB", Formatters.fileSize(1_048_576))
        assertEquals("2.5 GB", Formatters.fileSize(2_684_354_560L))
    }

    @Test
    fun defaultScanNameIsStableForAGivenInstantAndZone() {
        assertEquals("Scan 1970-01-01 00.00", Formatters.defaultScanName(0L, ZoneOffset.UTC))
        assertEquals("Scan 1970-01-01 05.30", Formatters.defaultScanName(0L, ZoneId.of("Asia/Kolkata")))
    }

    @Test
    fun safeFileNameReplacesIllegalCharacters() {
        assertEquals("a_b_c", Formatters.safeFileName("a/b:c"))
        assertEquals("Invoice _ 2026_", Formatters.safeFileName("Invoice ? 2026*"))
    }

    @Test
    fun safeFileNameNeverReturnsAnEmptyName() {
        assertEquals("Document", Formatters.safeFileName(""))
        assertEquals("Document", Formatters.safeFileName("   "))
    }

    @Test
    fun safeFileNameIsLimitedInLength() {
        assertTrue(Formatters.safeFileName("x".repeat(200)).length <= 80)
    }

    @Test
    fun stripPdfExtensionIsCaseInsensitiveAndOnlyAffectsTheEnd() {
        assertEquals("report", Formatters.stripPdfExtension("report.pdf"))
        assertEquals("report", Formatters.stripPdfExtension("report.PDF"))
        assertEquals("report.pdf.txt", Formatters.stripPdfExtension("report.pdf.txt"))
        assertEquals("report", Formatters.stripPdfExtension("report"))
    }
}
