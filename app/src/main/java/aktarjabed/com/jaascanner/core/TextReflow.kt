package aktarjabed.com.jaascanner.core

/**
 * Turns the text a PDF gives page by page (hard line breaks everywhere) into paragraphs that can
 * be reflowed on a phone screen, and cuts text into pieces a speech engine accepts.
 */
object TextReflow {

    private const val SENTENCE_END = ".!?:;\"'”’)।"
    private const val SHORT_LINE_RATIO = 0.6f
    private const val HEADING_RATIO = 0.5f

    fun paragraphs(pageText: String): List<String> {
        val lines = pageText.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trim() }
        val longest = lines.maxOfOrNull { it.length } ?: 0
        val result = ArrayList<String>()
        val current = StringBuilder()
        var currentLines = 0
        var lastLength = 0
        var currentIsHeading = false

        fun flush() {
            if (current.isNotBlank()) result.add(current.toString().trim())
            current.clear()
            currentLines = 0
            currentIsHeading = false
        }

        for (line in lines) {
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (current.isEmpty()) {
                current.append(line)
                currentLines = 1
                lastLength = line.length
                currentIsHeading = line.length < longest * HEADING_RATIO && line.last() !in SENTENCE_END
                continue
            }
            val previous = current.last()
            val endsSentence = previous in SENTENCE_END
            val shortBefore = lastLength < longest * SHORT_LINE_RATIO
            when {
                currentIsHeading && currentLines == 1 -> {
                    flush()
                    current.append(line)
                    currentLines = 1
                    currentIsHeading = line.length < longest * HEADING_RATIO && line.last() !in SENTENCE_END
                }
                endsSentence && shortBefore -> {
                    flush()
                    current.append(line)
                    currentLines = 1
                    currentIsHeading = false
                }
                previous == '-' && line.first().isLowerCase() -> {
                    current.setLength(current.length - 1)
                    current.append(line)
                    currentLines++
                }
                else -> {
                    current.append(' ').append(line)
                    currentLines++
                }
            }
            lastLength = line.length
        }
        flush()
        return result
    }

    /**
     * Pieces of at most [maxLength] characters (speech engines refuse long input), cut at sentence
     * ends where possible, then at spaces, then anywhere.
     */
    fun speechChunks(text: String, maxLength: Int = 3500): List<String> {
        require(maxLength >= 10) { "maxLength is too small" }
        val clean = text.replace(Regex("\\s*\\n\\s*"), "\n").trim()
        if (clean.isEmpty()) return emptyList()
        val sentences = clean.split(Regex("(?<=[.!?।])\\s+|\\n+")).filter { it.isNotBlank() }
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) chunks.add(current.toString().trim())
            current.clear()
        }
        for (sentence in sentences) {
            var rest = sentence.trim()
            while (rest.length > maxLength) {
                flush()
                val space = rest.lastIndexOf(' ', maxLength)
                val requested = if (space < maxLength / 2) maxLength else space
                val cut = safeCut(rest, requested)
                chunks.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trim()
            }
            if (current.length + rest.length + 1 > maxLength) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(rest)
        }
        flush()
        return chunks
    }

    /**
     * Never cuts between the two halves of a surrogate pair. A speech engine given a lone
     * surrogate either rejects the whole utterance or speaks a replacement character.
     */
    private fun safeCut(text: String, requested: Int): Int {
        var cut = requested.coerceIn(1, text.length)
        if (cut < text.length && text[cut - 1].isHighSurrogate() && text[cut].isLowSurrogate()) cut--
        return cut
    }
}
