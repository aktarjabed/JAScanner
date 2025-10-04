package com.jascanner.ai.ocr

import android.graphics.Bitmap
import javax.inject.Inject

class AdvancedSmartOcrEngine @Inject constructor(
    private val ocrProcessor: RobustOcrProcessor
) : SmartOcrEngine {
    override suspend fun recognize(bitmap: Bitmap): SmartOcrEngine.Result {
        return when (val result = ocrProcessor.processImage(bitmap)) {
            is RobustOcrProcessor.Result.Success -> {
                val smartResult = SmartOcrEngine.SmartOcrResult(
                    text = result.data.text,
                    confidence = result.data.confidence
                )
                SmartOcrEngine.Result.Success(smartResult)
            }
            is RobustOcrProcessor.Result.Error -> {
                SmartOcrEngine.Result.Error(result.exception)
            }
        }
    }
}