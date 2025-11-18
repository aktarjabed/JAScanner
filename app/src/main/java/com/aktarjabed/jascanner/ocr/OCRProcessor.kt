package com.aktarjabed.jascanner.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OCRProcessor {

    suspend fun recognizeText(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            val sb = StringBuilder()
            for (block in result.textBlocks) {
                sb.append(block.text)
                sb.append("\n")
            }
            sb.toString().trim()
        } catch (t: Throwable) {
            Log.e(TAG, "OCR failed", t)
            ""
        }
    }

    private const val TAG = "OCRProcessor"
}