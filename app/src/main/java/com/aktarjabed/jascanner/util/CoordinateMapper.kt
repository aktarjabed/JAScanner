package com.aktarjabed.jascanner.util

import android.graphics.PointF
import android.view.View
import androidx.camera.view.PreviewView
import org.opencv.core.Point
import kotlin.math.max
import kotlin.math.min

object CoordinateMapper {

    fun mapPoints(
        points: List<Point>,
        analysisWidth: Int,
        analysisHeight: Int,
        previewView: PreviewView,
        rotationDegrees: Int
    ): List<Point> {
        val viewW = previewView.width.toFloat()
        val viewH = previewView.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return points // fallback

        // Determine input and output aspect ratios
        val inRatio = analysisWidth.toFloat() / analysisHeight.toFloat()
        val outRatio = viewW / viewH

        // Compute scale and offsets for CENTER_CROP
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (inRatio > outRatio) {
            // Input is wider -> height fits, width crops
            scale = viewH / analysisHeight.toFloat()
            val scaledW = analysisWidth * scale
            offsetX = (viewW - scaledW) * 0.5f
            offsetY = 0f
        } else {
            // Input is taller -> width fits, height crops
            scale = viewW / analysisWidth.toFloat()
            val scaledH = analysisHeight * scale
            offsetY = (viewH - scaledH) * 0.5f
            offsetX = 0f
        }

        // Map each point
        val mapped = points.map { p ->
            val px = p.x.toFloat()
            val py = p.y.toFloat()

            // Apply scale + crop offsets
            val scaledX = px * scale + offsetX
            val scaledY = py * scale + offsetY

            // Apply rotation on output view coordinates
            applyRotation(
                x = scaledX,
                y = scaledY,
                viewW = viewW,
                viewH = viewH,
                rotationDegrees = rotationDegrees
            )
        }

        return mapped.map { Point(it.x.toDouble(), it.y.toDouble()) }
    }

    private fun applyRotation(
        x: Float,
        y: Float,
        viewW: Float,
        viewH: Float,
        rotationDegrees: Int
    ): PointF {
        return when (rotationDegrees) {
            0 -> PointF(x, y)
            90 -> PointF(y, viewW - x)
            180 -> PointF(viewW - x, viewH - y)
            270 -> PointF(viewH - y, x)
            else -> PointF(x, y)
        }
    }
}