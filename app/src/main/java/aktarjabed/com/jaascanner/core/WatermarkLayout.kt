package aktarjabed.com.jaascanner.core

/** Where on a page a watermark is stamped; u to the right and v downwards, both 0..1. */
data class WatermarkSpot(val centerU: Float, val centerV: Float)

object WatermarkLayout {

    fun centre(): List<WatermarkSpot> = listOf(WatermarkSpot(0.5f, 0.5f))

    /**
     * A repeating pattern: [columns] x [rows] cells, every second row shifted half a cell sideways
     * so the stamps do not line up in rigid columns.
     */
    fun tiled(columns: Int, rows: Int): List<WatermarkSpot> {
        require(columns >= 1 && rows >= 1) { "At least one column and one row" }
        val spots = ArrayList<WatermarkSpot>(columns * rows)
        for (row in 0 until rows) {
            // With a single column there is nothing to stagger against, and the 0.5-cell shift
            // below would push that one stamp fully off the page and drop the whole row.
            val shift = if (columns > 1 && row % 2 == 1) 0.5f else 0f
            for (column in 0 until columns) {
                val u = (column + 0.5f + shift) / columns
                // The shifted row would put its last stamp half off the page, so it keeps one fewer.
                if (shift > 0f && u > 1f - 0.5f / columns) continue
                spots.add(WatermarkSpot(u, (row + 0.5f) / rows))
            }
        }
        return spots
    }
}
