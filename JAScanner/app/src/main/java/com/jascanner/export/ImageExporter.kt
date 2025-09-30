package com.jascanner.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageExporter @Inject constructor(@ApplicationContext private val context: Context) {

    fun save(
        bitmap: Bitmap,
        format: ExportFormat,
        quality: QualityProfile,
        fileName: String
    ): Boolean {
        var success = false
        val fullName = "$fileName.${format.extension}"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                resolver.openOutputStream(imageUri!!)?.use {
                    val compressFormat = when (format) {
                        ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
                        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                        ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
                    }
                    val qualityValue = when (format) {
                        ExportFormat.JPG -> quality.jpegQuality
                        ExportFormat.WEBP -> quality.webpQuality
                        else -> 100 // PNG is lossless, quality is ignored
                    }
                    bitmap.compress(compressFormat, qualityValue, it)
                    success = true
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, fullName)
                FileOutputStream(image).use {
                    val compressFormat = when (format) {
                        ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
                        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                        ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
                    }
                     val qualityValue = when (format) {
                        ExportFormat.JPG -> quality.jpegQuality
                        ExportFormat.WEBP -> quality.webpQuality
                        else -> 100
                    }
                    bitmap.compress(compressFormat, qualityValue, it)
                    success = true
                }
            }
        } catch (e: Exception) {
            // Log error
            return false
        }
        return success
    }
}