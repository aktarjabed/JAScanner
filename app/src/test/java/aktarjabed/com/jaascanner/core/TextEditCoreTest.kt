package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditCoreTest {

    @Test
    fun singleLetterChangeIsOneCharacterRange() {
        assertEquals(TextChange(2, 3, 3), TextDiff.change("Rate", "Raxe"))
    }

    @Test
    fun insertionAndDeletion() {
        assertEquals(TextChange(3, 3, 4), TextDiff.change("Ram", "Rama"))
        assertEquals(TextChange(1, 2, 1), TextDiff.change("ab", "a"))
        assertNull(TextDiff.change("same", "same"))
    }

    @Test
    fun repeatedLettersDoNotOverlapPrefixAndSuffix() {
        val change = TextDiff.change("aaa", "aa")!!
        assertEquals(1, change.oldEnd - change.start)
        assertEquals(0, change.newEnd - change.start)
    }

    @Test
    fun surrogatePairIsNeverSplit() {
        // U+1F600 and U+1F601 ("grinning face" / "grinning face with smiling eyes") share the same
        // leading (high) surrogate, 0xD83D, so a naive char-by-char diff would land its boundary
        // between the high and low surrogate of U+1F600 and cut a surrogate pair in half.
        val grinning = "Hi 😀!"
        val grinningEyes = "Hi 😁!"
        val change = TextDiff.change(grinning, grinningEyes)!!
        // The boundary must sit on a whole-character edge, not mid-surrogate: slicing old/new at
        // these indices must not produce a lone surrogate.
        assertFalse(Character.isLowSurrogate(grinning[change.start]))
        assertFalse(Character.isLowSurrogate(grinning.getOrElse(change.oldEnd) { ' ' }))
        assertEquals(grinningEyes, grinning.substring(0, change.start) + "😁" + grinning.substring(change.oldEnd))
    }

    @Test
    fun fontNamesAreRecognised() {
        val bold = FontGuessing.guess("ABCDEF+Arial-BoldMT")
        assertEquals(FontKind.Sans, bold.kind)
        assertTrue(bold.bold)
        assertFalse(bold.italic)
        assertEquals("Arial-BoldMT", bold.cleanName)
        assertEquals(FontKind.Serif, FontGuessing.guess("TimesNewRomanPS-ItalicMT").kind)
        assertTrue(FontGuessing.guess("TimesNewRomanPS-ItalicMT").italic)
        assertEquals(FontKind.Mono, FontGuessing.guess("CourierNewPSMT").kind)
        assertEquals(FontKind.Script, FontGuessing.guess("DancingScript-Regular").kind)
        assertEquals(FontKind.Sans, FontGuessing.guess(null).kind)
    }

    @Test
    fun subsetPrefixDetection() {
        assertTrue(FontGuessing.isSubset("ABCDEF+Calibri"))
        assertFalse(FontGuessing.isSubset("Calibri"))
        assertFalse(FontGuessing.isSubset(null))
    }

    @Test
    fun blackTextOnWhiteIsDetected() {
        val w = 20
        val h = 10
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 3..6) for (x in 6..13) pixels[y * w + x] = 0xFF101010.toInt()
        val found = ColorSampling.detect(pixels, w, h, left = 4, top = 2, right = 16, bottom = 8)
        assertTrue(found.foundInk)
        assertEquals(0xFFFFFFFF.toInt(), found.backgroundArgb)
        assertEquals(0xFF101010.toInt(), found.textArgb)
    }

    @Test
    fun emptyAreaFallsBackToContrastingColour() {
        val pixels = IntArray(100) { 0xFF202020.toInt() }
        val found = ColorSampling.detect(pixels, 10, 10, 2, 2, 8, 8)
        assertFalse(found.foundInk)
        assertEquals(0xFFFFFFFF.toInt(), found.textArgb)
    }
}
