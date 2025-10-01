package com.jascanner.domain.model

import android.graphics.PointF
import android.graphics.RectF

sealed class Annotation {
    abstract val id: String
    abstract val pageId: String
    abstract val timestamp: Long
    abstract val layer: Int

    data class InkAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val points: List<PointF>,
        val strokeWidth: Float,
        val color: Int,
        val opacity: Float = 1f
    ) : Annotation()

    data class HighlightAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val boundingBox: RectF,
        val color: Int,
        val opacity: Float = 0.3f
    ) : Annotation()

    data class TextAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val text: String,
        val boundingBox: RectF,
        val fontSize: Float,
        val color: Int,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : Annotation()

    data class StampAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val stampType: StampType,
        val boundingBox: RectF
    ) : Annotation()

    data class RedactionAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val boundingBox: RectF,
        val isApplied: Boolean = false,
        val reason: String? = null
    ) : Annotation()

    data class SignatureAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val boundingBox: RectF,
        val signatureData: SignatureData
    ) : Annotation()

    data class ShapeAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        override val layer: Int = 0,
        val shapeType: ShapeType,
        val boundingBox: RectF,
        val strokeColor: Int,
        val strokeWidth: Float,
        val fillColor: Int?,
        val startPoint: PointF,
        val endPoint: PointF,
        val arrowHeadSize: Float = 20f
    ) : Annotation()
}

enum class StampType(val displayName: String) {
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CONFIDENTIAL("Confidential"),
    DRAFT("Draft"),
    FINAL("Final"),
    URGENT("Urgent"),
    RECEIVED("Received"),
    SIGNED("Signed")
}

data class SignatureData(
    val id: String,
    val paths: List<List<PointF>>,
    val bitmap: android.graphics.Bitmap,
    val timestamp: Long,
    val signerName: String? = null
)

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    OVAL
}