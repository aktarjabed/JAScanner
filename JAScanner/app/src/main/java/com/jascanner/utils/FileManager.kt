package com.jascanner.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun createDocumentDirectory(documentId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "documents/$documentId")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalStateException("Failed to create directory")
            }

            Result.success(dir)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create document directory")
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "documents/$documentId")
            val deleted = dir.deleteRecursively()

            if (deleted) {
                Timber.d("Deleted document directory: $documentId")
            } else {
                Timber.w("Failed to delete document directory: $documentId")
            }

            Result.success(deleted)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting document")
            Result.failure(e)
        }
    }

    suspend fun getDocumentSize(documentId: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "documents/$documentId")
            val size = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            Result.success(size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate document size")
            Result.failure(e)
        }
    }

    suspend fun copyFile(source: File, destination: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!source.exists()) {
                throw IllegalArgumentException("Source file does not exist")
            }

            destination.parentFile?.mkdirs()

            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            Timber.d("Copied file: ${source.name} to ${destination.absolutePath}")
            Result.success(true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy file")
            Result.failure(e)
        }
    }

    suspend fun calculateFileHash(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }

            val hash = md.digest().joinToString("") { "%02x".format(it) }
            Result.success(hash)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate file hash")
            Result.failure(e)
        }
    }

    suspend fun validateFile(file: File): FileValidationResult = withContext(Dispatchers.IO) {
        try {
            when {
                !file.exists() -> FileValidationResult.Invalid("File does not exist")
                !file.canRead() -> FileValidationResult.Invalid("File is not readable")
                file.length() == 0L -> FileValidationResult.Invalid("File is empty")
                file.length() > MAX_FILE_SIZE -> FileValidationResult.Invalid("File exceeds maximum size")
                else -> FileValidationResult.Valid
            }
        } catch (e: Exception) {
            Timber.e(e, "File validation failed")
            FileValidationResult.Invalid("Validation error: ${e.message}")
        }
    }

    suspend fun cleanupOldFiles(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Result<Int> =
        withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            var deletedCount = 0

            val docsDir = File(context.filesDir, "documents")
            if (docsDir.exists()) {
                docsDir.walkTopDown().forEach { file ->
                    if (file.isFile && (now - file.lastModified()) > maxAgeMillis) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }

            Timber.d("Cleaned up $deletedCount old files")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old files")
            Result.failure(e)
        }
    }

    suspend fun getFileInfo(file: File): FileInfo? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null

            FileInfo(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                canRead = file.canRead(),
                canWrite = file.canWrite()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file info")
            null
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100 MB
    }
}

sealed class FileValidationResult {
    object Valid : FileValidationResult()
    data class Invalid(val reason: String) : FileValidationResult()
}

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean
)