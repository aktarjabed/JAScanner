package aktarjabed.com.jaascanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkLayoutTest {

    @Test
    fun centreIsOneSpotInTheMiddle() {
        val spots = WatermarkLayout.centre()
        assertEquals(1, spots.size)
        assertEquals(0.5f, spots[0].centerU, 0.0001f)
        assertEquals(0.5f, spots[0].centerV, 0.0001f)
    }

    @Test
    fun singleColumnKeepsOneStampPerRow() {
        // Regression: the stagger shift used to make every odd row's only stamp land fully off
        // the page (u == 1.0), which the boundary check then dropped, leaving those rows empty.
        val spots = WatermarkLayout.tiled(columns = 1, rows = 4)
        assertEquals(4, spots.size)
        assertEquals(setOf(0, 1, 2, 3), spots.map { ((it.centerV * 4) - 0.5f).toInt() }.toSet())
        assertTrue(spots.all { it.centerU == 0.5f })
    }

    @Test
    fun multiColumnStaggerStillDropsOneOverflowingStamp() {
        // Unchanged behaviour for columns > 1: the shifted row keeps one fewer stamp.
        val spots = WatermarkLayout.tiled(columns = 2, rows = 2)
        assertEquals(3, spots.size)
    }
}
