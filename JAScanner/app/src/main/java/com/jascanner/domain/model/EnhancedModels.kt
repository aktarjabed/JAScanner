package com.jascanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// Image enhancement models
data class ImageAdjustments(
    val filter: ImageFilter?,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float
)

enum class ImageFilter(val displayName: String, val icon: ImageVector) {
    GRAYSCALE("Grayscale", Icons.Default.FilterBAndW),
    BLACK_AND_WHITE("Black & White", Icons.Default.Contrast),
    WHITEBOARD("Whiteboard", Icons.Default.Dashboard),
    MAGIC_COLOR("Magic Color", Icons.Default.AutoFixHigh),
    SEPIA("Sepia", Icons.Default.FilterVintage),
    NEGATIVE("Negative", Icons.Default.InvertColors)
}

// Edge detection models
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

// Shape annotation models
enum class ShapeType(val displayName: String, val icon: ImageVector) {
    RECTANGLE("Rectangle", Icons.Default.CropSquare),
    CIRCLE("Circle", Icons.Default.Circle),
    ELLIPSE("Ellipse", Icons.Default.Panorama),
    ARROW("Arrow", Icons.Default.ArrowForward),
    LINE("Line", Icons.Default.Remove)
}

// Saved signature model
data class SavedSignature(
    val id: String,
    val paths: List<List<PointF>>,
    val timestamp: Long,
    val signerName: String?,
    val bitmap: Bitmap?
)

// Add to EditablePage
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
    val cropRect: android.graphics.RectF? = null,
    val width: Int = 0,
    val height: Int = 0,
    val modifiedAt: Long = System.currentTimeMillis(),
    // New fields
    val detectedEdges: List<EdgePoint>? = null,
    val imageAdjustments: ImageAdjustments? = null
)
