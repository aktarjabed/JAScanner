package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFiltersTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun linesOfOneParagraphAreJoined() {
        val text = "This is the first line of a paragraph that goes on and on and on\nand continues here on the second line of the same paragraph."
        val result = TextReflow.paragraphs(text)
        assertEquals(1, result.size)
        assertTrue(result[0].contains("on and on and on and continues here"))
    }

    @Test
    fun blankLineStartsANewParagraph() {
        val result = TextReflow.paragraphs("First paragraph line.\n\nSecond paragraph line.")
        assertEquals(listOf("First paragraph line.", "Second paragraph line."), result)
    }

    @Test
    fun hyphenatedWordsAreRejoined() {
        val text = "The committee considered the proposal at some length and found it accept-\nable in most respects, with a few changes to be made later on."
        val result = TextReflow.paragraphs(text)
        assertEquals(1, result.size)
        assertTrue(result[0].contains("acceptable"))
    }

    @Test
    fun aShortLineWithoutAFullStopIsAHeading() {
        val text = "Chapter One\nIt was a bright cold day in April and the clocks were striking thirteen o'clock sharp."
        val result = TextReflow.paragraphs(text)
        assertEquals(listOf("Chapter One", "It was a bright cold day in April and the clocks were striking thirteen o'clock sharp."), result)
    }

    @Test
    fun aShortLastLineOfASentenceEndsTheParagraph() {
        val text = "A long line that fills the width of the page almost entirely and then some more.\n" +
            "End.\n" +
            "Next paragraph starts here with a long enough line to be the longest of them all really."
        val result = TextReflow.paragraphs(text)
        assertEquals(2, result.size)
        assertTrue(result[0].endsWith("End."))
    }

    @Test
    fun emptyAndBlankTextGivesNothing() {
        assertTrue(TextReflow.paragraphs("").isEmpty())
        assertTrue(TextReflow.paragraphs("  \n \n").isEmpty())
        assertTrue(TextReflow.speechChunks("   ").isEmpty())
    }

    @Test
    fun speechChunksRespectTheLimitAndKeepEveryWord() {
        val sentence = "The quick brown fox jumps over the lazy dog. "
        val text = sentence.repeat(300)
        val chunks = TextReflow.speechChunks(text, maxLength = 500)
        assertTrue(chunks.all { it.length <= 500 })
        assertTrue(chunks.size > 20)
        assertEquals(300 * 9, chunks.joinToString(" ").split(Regex("\\s+")).count { it.isNotEmpty() })
    }

    @Test
    fun aSingleHugeWordIsStillCut() {
        val chunks = TextReflow.speechChunks("x".repeat(1000), maxLength = 400)
        assertTrue(chunks.all { it.length <= 400 })
        assertEquals(1000, chunks.sumOf { it.length })
    }

    @Test
    fun devanagariDandaEndsASentence() {
        val chunks = TextReflow.speechChunks("पहला वाक्य। दूसरा वाक्य। तीसरा वाक्य।", maxLength = 20)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 20 })
    }

    @Test
    fun speechChunksNeverCutThroughASurrogatePair() {
        // Every emoji is two UTF-16 units. An odd limit lands inside the pair on every cut.
        val text = "😀".repeat(500)
        val chunks = TextReflow.speechChunks(text, maxLength = 101)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 101)
            assertFalse("chunk starts with a lone low surrogate", chunk.first().isLowSurrogate())
            assertFalse("chunk ends with a lone high surrogate", chunk.last().isHighSurrogate())
            var i = 0
            while (i < chunk.length) {
                val c = chunk[i]
                if (c.isHighSurrogate()) {
                    assertTrue("high surrogate at end of chunk", i + 1 < chunk.length)
                    assertTrue("high surrogate not followed by low", chunk[i + 1].isLowSurrogate())
                    i += 2
                } else {
                    assertFalse("lone low surrogate", c.isLowSurrogate())
                    i++
                }
            }
        }
        assertEquals(text.length, chunks.joinToString("").length)
    }

    @Test
    fun lightenBrightensGreyAndKeepsBlackAndWhite() {
        val out = PixelFilters.lighten(intArrayOf(rgb(0, 0, 0), rgb(128, 128, 128), rgb(255, 255, 255)))
        assertEquals(0, red(out[0]))
        assertTrue(red(out[1]) > 160)
        assertEquals(255, red(out[2]))
    }

    @Test
    fun strongContrastDarkensInkAndWhitensPaper() {
        val out = PixelFilters.strongContrast(intArrayOf(rgb(90, 90, 90), rgb(230, 230, 230)))
        assertTrue(red(out[0]) < 70)
        assertTrue(red(out[1]) > 245)
    }

    @Test
    fun magicColorTurnsYellowPaperWhiteButKeepsInkDark() {
        val paper = rgb(232, 218, 170)
        val ink = rgb(30, 30, 40)
        val pixels = IntArray(1000) { if (it % 20 == 0) ink else paper }
        val out = PixelFilters.magicColor(pixels)
        assertTrue("red ${red(out[1])}", red(out[1]) > 235)
        assertTrue("green ${green(out[1])}", green(out[1]) > 235)
        assertTrue("blue ${blue(out[1])}", blue(out[1]) > 225)
        assertTrue(red(out[0]) < 60)
    }

    @Test
    fun magicColorSamplingDoesNotAliasAPeriodSevenPattern() {
        // A fixed every-7th-pixel sample would see only the black phase and set the paper level
        // to 0, blowing out the grey. Co-prime sampling sees all phases and keeps it mid-grey.
        val pixels = IntArray(700) { index ->
            when (index % 7) {
                0 -> rgb(0, 0, 0)
                1 -> rgb(100, 100, 100)
                else -> rgb(200, 200, 200)
            }
        }
        val out = PixelFilters.magicColor(pixels)
        assertTrue("grey phase was over-brightened: ${red(out[1])}", red(out[1]) < 200)
        assertEquals(0, red(out[0]))
    }

    @Test
    fun samplingCoversEveryPhaseOfAPeriodicImage() {
        val indices = PixelFilters.sampleIndices(size = 70_000, maxSamples = 1_000)
        assertEquals(1_000, indices.size)
        assertEquals((0..6).toSet(), indices.map { it % 7 }.toSet())
        assertEquals(indices.size, indices.toSet().size)
    }

    @Test
    fun magicColorCapsItsGainOnDarkPhotos() {
        val dark = IntArray(500) { rgb(20, 20, 20) }
        val out = PixelFilters.magicColor(dark)
        assertTrue(red(out[0]) < 80)
    }

    @Test
    fun filtersAlwaysReturnOpaquePixels() {
        val input = intArrayOf(0x00112233, 0x80FFFFFF.toInt())
        for (result in listOf(PixelFilters.lighten(input), PixelFilters.strongContrast(input), PixelFilters.magicColor(input))) {
            assertTrue(result.all { (it ushr 24) == 0xFF })
        }
    }

    @Test
    fun tiledWatermarkStaysInsideThePageAndStaggers() {
        val spots = WatermarkLayout.tiled(3, 4)
        assertTrue(spots.all { it.centerU > 0f && it.centerU < 1f && it.centerV > 0f && it.centerV < 1f })
        assertEquals(3 + 2 + 3 + 2, spots.size)
        assertEquals(1, WatermarkLayout.centre().size)
    }
}
