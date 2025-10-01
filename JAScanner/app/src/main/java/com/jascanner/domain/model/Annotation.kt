package com.jascanner.domain.model

import android.graphics.PointF

sealed class Annotation {
    abstract val id: String
    abstract val pageId: String
    abstract val timestamp: Long
    abstract val layer: Int

    data class ShapeAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val shapeType: ShapeType,
        val boundingBox: android.graphics.RectF,
        val strokeColor: Int,
        val strokeWidth: Float,
        val fillColor: Int?,
        val startPoint: PointF,
        val endPoint: PointF,
        val arrowHeadSize: Float = 20f
    ) : Annotation()
}