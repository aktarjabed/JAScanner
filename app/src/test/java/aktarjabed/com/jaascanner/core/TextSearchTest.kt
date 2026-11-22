package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun nonAsciiCaseIsFolded() {
        assertEquals(TextSearch.normalize("müller"), TextSearch.normalize("MÜLLER"))
        assertEquals(TextSearch.normalize("άλφα"), TextSearch.normalize("ΆΛΦΑ"))
    }

    @Test
    fun compatibilityCharactersMatchTheirPlainForm() {
        assertEquals("fi", TextSearch.normalize("ﬁ"))
        assertEquals(TextSearch.normalize("café"), TextSearch.normalize("cafe\u0301"))
    }

    @Test
    fun normalisedTextContainsTheNormalisedQuery() {
        val stored = TextSearch.normalize("Invoice für Müller")
        assertTrue(stored.contains(TextSearch.normalize("MÜLLER")))
    }
}
