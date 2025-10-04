package com.jascanner.ai.preprocess

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AdvancedAiPreprocessor @Inject constructor() : AiPreprocessor {
    override suspend fun process(bitmap: Bitmap): AiPreprocessor.Result {
        return withContext(Dispatchers.Default) {
            try {
                val grayscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(grayscaleBitmap)
                val paint = Paint()
                val colorMatrix = ColorMatrix()
                colorMatrix.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                AiPreprocessor.Result.Success(grayscaleBitmap)
            } catch (e: Exception) {
                AiPreprocessor.Result.Error(e)
            }
        }
    }
}