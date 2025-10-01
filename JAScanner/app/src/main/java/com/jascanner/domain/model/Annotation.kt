package com.jascanner.domain.model

import android.graphics.PointF

enum class StampType {
    APPROVED,
    REJECTED,
    CONFIDENTIAL,
    DRAFT,
    FINAL,
    URGENT,
    RECEIVED,
    SIGNED
}

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

enum class ShapeType(val displayName: String) {
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    ELLIPSE("Ellipse"),
    ARROW("Arrow"),
    LINE("Line")
}