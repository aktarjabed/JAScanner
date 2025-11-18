package com.aktarjabed.jascanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val corners = mutableListOf<PointF>()
    private var selectedCorner: Int = -1
    private val touchRadius = 60f

    private val linePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerStrokePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        initializeCorners()
        invalidate()
    }

    private fun initializeCorners() {
        corners.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = 50f
        corners.add(PointF(margin, margin))
        corners.add(PointF(w - margin, margin))
        corners.add(PointF(w - margin, h - margin))
        corners.add(PointF(margin, h - margin))
    }

    fun resetCorners() {
        initializeCorners()
        invalidate()
    }

    fun getCornerPoints(): List<PointF> {
        val bmp = bitmap ?: return emptyList()
        val scaleX = bmp.width.toFloat() / width
        val scaleY = bmp.height.toFloat() / height
        return corners.map { PointF(it.x * scaleX, it.y * scaleY) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let {
            canvas.drawBitmap(it, null, canvas.clipBounds, null)
        }

        if (corners.size == 4) {
            val path = Path()
            path.moveTo(corners[0].x, corners[0].y)
            path.lineTo(corners[1].x, corners[1].y)
            path.lineTo(corners[2].x, corners[2].y)
            path.lineTo(corners[3].x, corners[3].y)
            path.close()

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, linePaint)

            corners.forEach { corner ->
                canvas.drawCircle(corner.x, corner.y, 20f, cornerPaint)
                canvas.drawCircle(corner.x, corner.y, 20f, cornerStrokePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedCorner = findNearestCorner(event.x, event.y)
                return selectedCorner != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedCorner != -1) {
                    corners[selectedCorner].x = event.x.coerceIn(0f, width.toFloat())
                    corners[selectedCorner].y = event.y.coerceIn(0f, height.toFloat())
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                selectedCorner = -1
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNearestCorner(x: Float, y: Float): Int {
        corners.forEachIndexed { index, corner ->
            val dx = x - corner.x
            val dy = y - corner.y
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            if (distance < touchRadius) {
                return index
            }
        }
        return -1
    }

    fun drawableBitmap(): Bitmap? = bitmap
}