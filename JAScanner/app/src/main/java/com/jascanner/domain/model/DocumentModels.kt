package com.jascanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

data class EditableDocument(
    val id: String,
    val name: String,
    val pages: List<EditablePage>,
    val createdAt: Long,
    val modifiedAt: Long
)

data class EditablePage(
    val pageId: String,
    val pageNumber: Int,
    val originalImageUri: String,
    val processedImageUri: String? = null,
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    val ocrTextLayer: List<OcrTextBlock> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val transformMatrix: FloatArray = FloatArray(9) { if (it % 4 == 0) 1f else 0f },
    val rotation: Float = 0f,
    val cropRect: RectF? = null,
    val width: Int = 0,
    val height: Int = 0,
    val modifiedAt: Long = System.currentTimeMillis(),
    val detectedEdges: List<EdgePoint>? = null,
    val imageAdjustments: ImageAdjustments? = null,
    val editedText: String? = null
)

data class OcrTextBlock(
    val text: String,
    val boundingBox: RectF,
    val editedText: String? = null
)

data class ImageAdjustments(
    val filter: ImageFilter?,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float
)

enum class ImageFilter(val displayName: String) {
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("Black & White"),
    WHITEBOARD("Whiteboard"),
    MAGIC_COLOR("Magic Color"),
    SEPIA("Sepia"),
    NEGATIVE("Negative")
}

data class EdgePoint(
    val x: Float,
    val y: Float,
    val type: Type
) {
    enum class Type {
        CORNER,
        EDGE
    }
}