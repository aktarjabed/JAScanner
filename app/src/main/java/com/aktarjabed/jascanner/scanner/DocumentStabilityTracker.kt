package com.aktarjabed.jascanner.scanner

import org.opencv.core.Point
import kotlin.math.hypot

/**
 * Tracks last N detections and returns true if the corners have been stable
 * (i.e., not moving more than allowed px) for requiredStableFrames.
 */
class DocumentStabilityTracker(
    private val requiredStableFrames: Int = 3,
    private val maxCornerMovementPx: Double = 6.0
) {
    private val history = ArrayDeque<List<Point>>()

    fun push(corners: List<Point>): Boolean {
        if (corners.size != 4) {
            history.clear()
            return false
        }
        // clone points to be safe
        val copy = corners.map { Point(it.x, it.y) }
        history.addLast(copy)
        if (history.size > requiredStableFrames) history.removeFirst()
        if (history.size < requiredStableFrames) return false

        val base = history.first()
        // compare each frame to base
        for (frame in history) {
            for (i in 0 until 4) {
                val dx = frame[i].x - base[i].x
                val dy = frame[i].y - base[i].y
                val d = hypot(dx, dy)
                if (d > maxCornerMovementPx) return false
            }
        }
        return true
    }

    fun reset() {
        history.clear()
    }
}