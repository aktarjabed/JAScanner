package aktarjabed.com.jaascanner.core

/** A rectangle in PDF page space (origin bottom-left, points). */
data class CardSpot(val x: Float, val y: Float, val width: Float, val height: Float)

/** Places several card sides on one page, top to bottom, centred, at real card width when they fit. */
object CardLayout {

    /** Width of an ID-1 card (Aadhaar, PAN, driving licence, bank cards): 85.6 mm. */
    const val CARD_WIDTH_PT = 242.65f

    fun stack(
        aspects: List<Float>,
        pageWidth: Float,
        pageHeight: Float,
        margin: Float = 36f,
        gap: Float = 24f,
    ): List<CardSpot> {
        if (aspects.isEmpty()) return emptyList()
        // Guard a page smaller than twice the margin (a malformed or deliberately tiny page size)
        // so the layout math below never goes negative and produces inverted/garbage rectangles.
        val usableW = (pageWidth - 2 * margin).coerceAtLeast(1f)
        val usableH = (pageHeight - 2 * margin).coerceAtLeast(1f)
        val cardW = minOf(CARD_WIDTH_PT, usableW)
        val heights = aspects.map { cardW / it.coerceAtLeast(0.05f) }
        val total = heights.sum() + gap * (aspects.size - 1)
        val scale = if (total > usableH) usableH / total else 1f
        val w = cardW * scale
        var top = (pageHeight + total * scale) / 2f
        return heights.map { height ->
            val h = height * scale
            val spot = CardSpot((pageWidth - w) / 2f, top - h, w, h)
            top -= h + gap * scale
            spot
        }
    }
}
