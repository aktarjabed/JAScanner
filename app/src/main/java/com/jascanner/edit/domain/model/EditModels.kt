package com.jascanner.edit.domain.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.jascanner.core.data.Converters

@Entity(tableName = "editable_documents")
@TypeConverters(Converters::class)
data class EditableDocument(
    @PrimaryKey val id: String,
    val name: String,
    val pages: List<EditablePage>,
    val createdAt: Long,
    val modifiedAt: Long,
    val hasSignature: Boolean = false,
    val signatureInvalidated: Boolean = false
)

data class EditablePage(
    val pageId: String,
    val originalImageUri: String,
    val processedImageUri: String? = null,
    val ocrTextLayer: List<TextBlock> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val transformMatrix: FloatArray = FloatArray(9) { if (it % 4 == 0) 1f else 0f },
    val rotation: Float = 0f,
    val cropRect: RectF? = null
)

data class TextBlock(
    val id: String,
    val text: String,
    val boundingBox: RectF,
    val confidence: Float,
    val isEdited: Boolean = false,
    val editedText: String? = null
)

sealed class Annotation {
    abstract val id: String
    abstract val pageId: String
    abstract val timestamp: Long

    data class InkAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        val points: List<PointF>,
        val strokeWidth: Float,
        val color: Int
    ) : Annotation()

    data class TextAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        val text: String,
        val boundingBox: RectF,
        val fontSize: Float,
        val color: Int,
        val fontFamily: String = "Helvetica"
    ) : Annotation()

    data class HighlightAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        val boundingBox: RectF,
        val color: Int,
        val opacity: Float = 0.3f
    ) : Annotation()

    data class RedactionAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        val boundingBox: RectF
    ) : Annotation()

    data class SignatureAnnotation(
        override val id: String,
        override val pageId: String,
        override val timestamp: Long,
        val signatureImageUri: String,
        val boundingBox: RectF
    ) : Annotation()
}

data class EditAction(
    val id: String,
    val actionType: ActionType,
    val timestamp: Long,
    val pageId: String,
    val beforeState: Any?,
    val afterState: Any?
)

enum class ActionType {
    CROP, ROTATE, ANNOTATE, TEXT_EDIT, FILTER, REDACT, SIGNATURE
}