package com.aktarjabed.jascanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun createOutputFile(context: Context, prefix: String, suffix: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        dir.mkdirs()
        return File.createTempFile(prefix, suffix, dir)
    }

    fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int = 90) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
        }
    }

    fun copyUriToFile(context: Context, uri: Uri, targetName: String? = null): File? {
        return try {
            val resolver = context.contentResolver
            val displayName = targetName ?: queryDisplayName(context, uri) ?: "import_${System.currentTimeMillis()}"
            val out = File(context.cacheDir, displayName)
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
            out
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) { }
        return null
    }
}