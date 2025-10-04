package com.jascanner.ai.preprocess

import android.graphics.Bitmap

interface AiPreprocessor {
    sealed class Result {
        data class Success(val processedBitmap: Bitmap) : Result()
        data class Error(val exception: Exception) : Result()

        fun onSuccess(block: (Success) -> Unit): Result {
            if (this is Success) {
                block(this)
            }
            return this
        }
    }

    suspend fun process(bitmap: Bitmap): Result
}