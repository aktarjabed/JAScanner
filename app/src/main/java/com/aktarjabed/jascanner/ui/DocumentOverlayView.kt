package com.aktarjabed.jascanner.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point

/**
 * Simple overlay that draws a polygon and corner dots.
 * Call setCorners() with a list of OpenCV Points (screen coordinates).
 */
class DocumentOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint().apply {
        color = Color.argb(220, 0, 200, 0)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(36, 0, 200, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.argb(255, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var corners: List<Point>? = null

    fun setCorners(points: List<Point>) {
        corners = points
        postInvalidate()
    }

    fun clear() {
        corners = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = corners ?: return
        if (pts.size < 4) return

        val path = Path()
        path.moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
        for (i in 1 until pts.size) {
            path.lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        // draw corner dots
        for (p in pts) {
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 8f, cornerPaint)
        }
    }
}