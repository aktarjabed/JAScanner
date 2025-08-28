package com.jascanner.compression

import android.content.Context
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class AdvancedCompressionEngine @Inject constructor(private val context: Context) {
    data class CompressionOptions(val imageQuality: Int = 80, val maxWidth: Int = 1920, val maxHeight: Int = 1080)
    data class CompressionResult(val success: Boolean, val originalSize: Long, val compressedSize: Long, val ratio: Double, val error: String? = null)

    fun compressImage(input: File, output: File, options: CompressionOptions): CompressionResult = try {
        val original = input.length()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, opts)
        val sample = calculateSample(opts.outWidth, opts.outHeight, options.maxWidth, options.maxHeight)
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(input.absolutePath, decode) ?: throw IllegalStateException("Decode failed")
        FileOutputStream(output).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, options.imageQuality, it) }
        val size = output.length()
        CompressionResult(true, original, size, if (original > 0) 1 - size.toDouble() / original else 0.0)
    } catch (e: Exception) {
        Timber.e(e); CompressionResult(false, input.length(), 0, 0.0, e.message)
    }

    private fun calculateSample(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var sample = 1
        var cw = w; var ch = h
        while (cw / 2 >= maxW && ch / 2 >= maxH) { cw /= 2; ch /= 2; sample *= 2 }
        return sample
    }
}

