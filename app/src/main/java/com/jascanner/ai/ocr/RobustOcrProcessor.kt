package com.jascanner.ai.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jascanner.core.EnhancedErrorHandler
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobustOcrProcessor @Inject constructor(
    private val errorHandler: EnhancedErrorHandler
) {

    data class OcrResult(
        val text: String,
        val confidence: Float
    )

    sealed class Result<out T> {
        data class Success<out T>(val data: T) : Result<T>()
        data class Error(val exception: Exception) : Result<Nothing>()
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): Result<OcrResult> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            var totalConfidence = 0f
            var blockCount = 0
            for (block in result.textBlocks) {
                block.confidence?.let {
                    totalConfidence += it
                    blockCount++
                }
            }
            val averageConfidence = if (blockCount > 0) totalConfidence / blockCount else 0f

            val ocrResult = OcrResult(result.text, averageConfidence)
            Timber.d("OCR processing successful. Confidence: ${ocrResult.confidence}")
            Result.Success(ocrResult)
        } catch (e: Exception) {
            val errorMessage = "Failed to process image with OCR"
            Timber.e(e, errorMessage)
            errorHandler.recordException(e, errorMessage)
            Result.Error(e)
        }
    }
}