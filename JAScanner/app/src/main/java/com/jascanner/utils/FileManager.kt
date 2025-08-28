package com.jascanner.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(private val context: Context) {
    fun getAppDir(): File = context.filesDir
    fun createTempFile(prefix: String, suffix: String): File = File.createTempFile(prefix, suffix, context.cacheDir)
    fun getUriForFile(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    fun getSecureUriForFile(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.secureprovider", file)
    fun deleteFile(path: String): Boolean = try { val f = File(path); if (f.exists()) f.delete() else true } catch (e: Exception) { Timber.e(e); false }
    fun ensureDir(path: String): Boolean = File(path).let { if (!it.exists()) it.mkdirs() else true }
}

