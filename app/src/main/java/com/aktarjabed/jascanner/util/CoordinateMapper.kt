package com.aktarjabed.jascanner.util

import android.graphics.PointF
import androidx.camera.view.PreviewView
import org.opencv.core.Point

object CoordinateMapper {
    /**
     * Map points from analysis bitmap -> PreviewView coordinate system (centerCrop).
     * rotationDegrees: rotation to apply to the mapped coordinates (0,90,180,270)
     */
    fun mapPoints(
        points: List<Point>,
        analysisWidth: Int,
        analysisHeight: Int,
        previewView: PreviewView,
        rotationDegrees: Int
    ): List<Point> {
        val viewW = previewView.width.toFloat()
        val viewH = previewView.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return points

        val inRatio = analysisWidth.toFloat() / analysisHeight.toFloat()
        val outRatio = viewW / viewH

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (inRatio > outRatio) {
            // input wider -> height fits, width cropped
            scale = viewH / analysisHeight.toFloat()
            val scaledW = analysisWidth * scale
            offsetX = (viewW - scaledW) * 0.5f
            offsetY = 0f
        } else {
            // input taller -> width fits, height cropped
            scale = viewW / analysisWidth.toFloat()
            val scaledH = analysisHeight * scale
            offsetY = (viewH - scaledH) * 0.5f
            offsetX = 0f
        }

        return points.map { p ->
            val px = p.x.toFloat()
            val py = p.y.toFloat()
            val scaledX = px * scale + offsetX
            val scaledY = py * scale + offsetY
            val pf = applyRotation(scaledX, scaledY, viewW, viewH, rotationDegrees)
            Point(pf.x.toDouble(), pf.y.toDouble())
        }
    }

    private fun applyRotation(x: Float, y: Float, viewW: Float, viewH: Float, rotationDegrees: Int): PointF {
        return when (rotationDegrees) {
            0 -> PointF(x, y)
            90 -> PointF(y, viewW - x)
            180 -> PointF(viewW - x, viewH - y)
            270 -> PointF(viewH - y, x)
            else -> PointF(x, y)
        }
    }
}