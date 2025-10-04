package com.jascanner.ai.ocr

import android.graphics.Bitmap

interface SmartOcrEngine {
    data class SmartOcrResult(
        val text: String,
        val confidence: Float
    )

    sealed class Result {
        data class Success(val data: SmartOcrResult) : Result()
        data class Error(val exception: Exception) : Result()

        fun onSuccess(block: (Success) -> Unit): Result {
            if (this is Success) {
                block(this)
            }
            return this
        }
    }

    suspend fun recognize(bitmap: Bitmap): Result
}