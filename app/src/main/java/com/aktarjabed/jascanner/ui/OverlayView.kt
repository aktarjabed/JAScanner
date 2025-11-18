package com.aktarjabed.jascanner.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#3300FF00")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var pts: List<Point>? = null
    private val path = Path()

    fun setCorners(viewPoints: List<Point>) {
        if (viewPoints.size != 4) return
        pts = viewPoints
        invalidate()
    }

    fun clear() {
        pts = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pts ?: return
        path.reset()
        path.moveTo(p[0].x.toFloat(), p[0].y.toFloat())
        path.lineTo(p[1].x.toFloat(), p[1].y.toFloat())
        path.lineTo(p[2].x.toFloat(), p[2].y.toFloat())
        path.lineTo(p[3].x.toFloat(), p[3].y.toFloat())
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        for (corner in p) {
            canvas.drawCircle(corner.x.toFloat(), corner.y.toFloat(), 10f, pointPaint)
        }
    }
}