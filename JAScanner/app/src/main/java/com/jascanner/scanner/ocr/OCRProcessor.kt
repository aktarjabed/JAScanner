package com.jascanner.scanner.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jascanner.data.local.entities.ScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OCRProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap, documentId: Long, pageNumber: Int): Result<ScanResult> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()

            val fullText = visionText.text
            val confidence = calculateConfidence(visionText)

            val emails = extractEmails(fullText)
            val phones = extractPhoneNumbers(fullText)
            val urls = extractUrls(fullText)

            val scanResult = ScanResult(
                documentId = documentId,
                imagePath = "",
                ocrText = fullText,
                confidence = confidence,
                emails = emails,
                phones = phones,
                urls = urls,
                pageNumber = pageNumber
            )

            Timber.i("OCR completed: ${fullText.length} chars, confidence: $confidence")
            Result.success(scanResult)

        } catch (e: Exception) {
            Timber.e(e, "OCR processing failed")
            Result.failure(e)
        }
    }

    private fun calculateConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        return 0.92f // Simplified
    }

    private fun extractEmails(text: String): List<String> {
        val regex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}".toRegex()
        return regex.findAll(text).map { it.value }.toList()
    }

    private fun extractPhoneNumbers(text: String): List<String> {
        val regex = "\\+?\\d[\\d -]{8,12}\\d".toRegex()
        return regex.findAll(text).map { it.value }.toList()
    }

    private fun extractUrls(text: String): List<String> {
        val regex = "(https?://[^\\s]+)".toRegex()
        return regex.findAll(text).map { it.value }.toList()
    }
}