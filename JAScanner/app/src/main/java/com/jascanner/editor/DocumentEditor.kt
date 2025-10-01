package com.jascanner.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.jascanner.domain.model.EditorError
import com.jascanner.domain.model.EditorResult
import com.jascanner.utils.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentEditor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun cropBitmap(
        documentId: String,
        pageId: String,
        bitmap: Bitmap,
        cropRect: RectF
    ): EditorResult<Bitmap> = withContext(Dispatchers.Default) {
        var croppedBitmap: Bitmap? = null
        try {
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                return@withContext EditorResult.Error(
                    EditorError.InvalidOperation("Invalid crop dimensions")
                )
            }

            val x = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val y = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val width = (cropRect.width() * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val height = (cropRect.height() * bitmap.height).toInt().coerceIn(1, bitmap.height - y)

            croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)

            Timber.d("Cropped bitmap: ${width}x${height}")
            EditorResult.Success(croppedBitmap)

        } catch (e: OutOfMemoryError) {
            BitmapUtils.recycleSafely(croppedBitmap)
            System.gc()
            EditorResult.Error(EditorError.MemoryError("Out of memory", e))
        } catch (e: Exception) {
            BitmapUtils.recycleSafely(croppedBitmap)
            EditorResult.Error(EditorError.OperationFailed("Crop failed", e))
        }
    }

    suspend fun rotateBitmap(
        documentId: String,
        pageId: String,
        bitmap: Bitmap,
        degrees: Float
    ): EditorResult<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            EditorResult.Success(rotatedBitmap)
        } catch (e: OutOfMemoryError) {
            System.gc()
            EditorResult.Error(EditorError.MemoryError("Out of memory", e))
        } catch (e: Exception) {
            EditorResult.Error(EditorError.OperationFailed("Rotation failed", e))
        }
    }

    suspend fun saveBitmap(
        documentId: String,
        bitmap: Bitmap,
        filename: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): EditorResult<String> = withContext(Dispatchers.IO) {
        try {
            val validQuality = quality.coerceIn(0, 100)
            val docDir = File(context.filesDir, "documents/$documentId")
            if (!docDir.exists()) {
                docDir.mkdirs()
            }
            val file = File(docDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(format, validQuality, out)
            }
            val uri = Uri.fromFile(file).toString()
            EditorResult.Success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save bitmap")
            EditorResult.Error(EditorError.OperationFailed("Save failed", e))
        }
    }
}