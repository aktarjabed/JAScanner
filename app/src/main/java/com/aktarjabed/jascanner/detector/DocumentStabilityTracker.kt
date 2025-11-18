package com.aktarjabed.jascanner.detector

import org.opencv.core.Point
import kotlin.math.hypot

class DocumentStabilityTracker(
    private val requiredStableFrames: Int = 3,
    private val maxCornerMovementPx: Double = 12.0
) {
    private val history = ArrayDeque<List<Point>>(requiredStableFrames)

    fun push(corners: List<Point>): Boolean {
        if (history.isEmpty()) {
            history.addLast(corners)
            return false
        }

        val last = history.last()
        if (!areCornersComparable(last, corners)) {
            // Out-of-order or invalid comparison — reset
            history.clear()
            history.addLast(corners)
            return false
        }

        history.addLast(corners)
        if (history.size > requiredStableFrames) history.removeFirst()

        // Check stability: max movement between min and max of history for each corner < threshold
        if (history.size < requiredStableFrames) return false

        for (i in 0 until 4) {
            var minX = Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxX = Double.MIN_VALUE
            var maxY = Double.MIN_VALUE
            for (frame in history) {
                val p = frame[i]
                minX = minOf(minX, p.x)
                maxX = maxOf(maxX, p.x)
                minY = minOf(minY, p.y)
                maxY = maxOf(maxY, p.y)
            }
            val move = hypot(maxX - minX, maxY - minY)
            if (move > maxCornerMovementPx) return false
        }
        return true
    }

    fun reset() = history.clear()

    private fun areCornersComparable(a: List<Point>, b: List<Point>): Boolean {
        if (a.size != 4 || b.size != 4) return false
        // Optionally compare area or centroid closeness
        return true
    }
}