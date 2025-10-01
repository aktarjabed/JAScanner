package com.jascanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

// PDF Operation Models
data class MergeOptions(
    val specificPages: List<Int> = emptyList(),
    val continueOnError: Boolean = true
)

data class SplitOptions(
    val mode: SplitMode,
    val pageRanges: List<Pair<Int, Int>> = emptyList(),
    val pagesPerFile: Int = 1,
    val continueOnError: Boolean = true
)

enum class SplitMode {
    BY_PAGE,
    BY_RANGE,
    BY_SIZE
}

data class Watermark(
    val text: String,
    val fontSize: Float = 48f,
    val color: Int = android.graphics.Color.GRAY,
    val opacity: Float = 0.3f,
    val rotation: Float = -45f,
    val position: WatermarkPosition = WatermarkPosition.OVERLAY,
    val horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER,
    val verticalAlign: VerticalAlign = VerticalAlign.MIDDLE
)

enum class WatermarkPosition {
    OVERLAY,
    UNDERLAY
}

enum class HorizontalAlign {
    LEFT,
    CENTER,
    RIGHT
}

enum class VerticalAlign {
    TOP,
    MIDDLE,
    BOTTOM
}

data class RedactionArea(
    val pageNumber: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

// Text Editing Models
data class TextFormatting(
    val fontSize: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColor: Int = android.graphics.Color.BLACK,
    val highlightColor: Int = android.graphics.Color.TRANSPARENT
) {
    companion object {
        fun default() = TextFormatting()
    }
}

data class TextFormattingSpan(
    val formatting: TextFormatting,
    val start: Int,
    val end: Int
)

data class FindReplaceOptions(
    val findQuery: String,
    val replaceQuery: String,
    val caseSensitive: Boolean,
    val wholeWord: Boolean,
    val useRegex: Boolean
)

data class FindOptions(
    val caseSensitive: Boolean,
    val wholeWord: Boolean,
    val useRegex: Boolean
)

data class SpellCheckSuggestion(
    val word: String,
    val startIndex: Int,
    val endIndex: Int,
    val suggestions: List<String>
)

// UX/UI Models
data class ComparisonState(
    val beforeBitmap: Bitmap?,
    val afterBitmap: Bitmap?,
    val zoomLevel: Float = 1.0f,
    val panOffset: PointF = PointF(0f, 0f)
)

data class BatchOperation(
    val operationType: String,
    val documents: List<EditableDocument>,
    val status: BatchOperationStatus = BatchOperationStatus.PENDING,
    val progress: Int = 0
)

enum class BatchOperationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class EditingPreset(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val filter: ImageFilter?,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float,
    val cropRect: RectF? = null,
    val rotation: Float = 0f
)

// Performance & System Models
data class EditorStatistics(
    val activeOperations: Int,
    val cachedBitmaps: Int,
    val totalErrors: Int,
    val successfulOperations: Long,
    val failedOperations: Long,
    val memoryUsage: MemoryUsage,
    val storageInfo: StorageInfo
)

data class MemoryUsage(
    val maxMemory: Long,
    val totalMemory: Long,
    val freeMemory: Long,
    val usedMemory: Long
) {
    val usagePercentage: Float
        get() = if (maxMemory > 0) (usedMemory * 100f) / maxMemory else 0f
}

data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usableSpace: Long
)











sealed class EditorResult<out T> {
    data class Success<T>(val data: T) : EditorResult<T>()
    data class Error(val error: EditorError) : EditorResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error.exception ?: Exception(error.message)
    }
}

sealed class EditorError(open val message: String, open val exception: Throwable? = null) {
    data class InvalidOperation(override val message: String) : EditorError(message)
    data class MemoryError(override val message: String, override val exception: Throwable?) : EditorError(message, exception)
    data class OperationFailed(override val message: String, override val exception: Throwable?) : EditorError(message, exception)
    data class FileError(override val message: String, override val exception: Throwable?) : EditorError(message, exception)
}