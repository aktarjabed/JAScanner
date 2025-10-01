package com.jascanner.utils

import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHandler {

    fun handleError(error: Throwable): ErrorInfo {
        Timber.e(error, "Handling error")

        return when (error) {
            is OutOfMemoryError -> ErrorInfo(
                type = ErrorType.MEMORY,
                message = "Out of memory. Please try closing other apps.",
                isCritical = true,
                isRecoverable = true,
                suggestedAction = "Close other apps and try again"
            )

            is IOException -> ErrorInfo(
                type = ErrorType.IO,
                message = "File operation failed: ${error.message}",
                isCritical = false,
                isRecoverable = true,
                suggestedAction = "Check storage space and permissions"
            )

            is SocketTimeoutException, is UnknownHostException -> ErrorInfo(
                type = ErrorType.NETWORK,
                message = "Network connection failed",
                isCritical = false,
                isRecoverable = true,
                suggestedAction = "Check internet connection and try again"
            )

            is IllegalArgumentException, is IllegalStateException -> ErrorInfo(
                type = ErrorType.VALIDATION,
                message = "Invalid operation: ${error.message}",
                isCritical = false,
                isRecoverable = false,
                suggestedAction = "Please check your input"
            )

            is SecurityException -> ErrorInfo(
                type = ErrorType.PERMISSION,
                message = "Permission denied: ${error.message}",
                isCritical = true,
                isRecoverable = false,
                suggestedAction = "Grant required permissions in settings"
            )

            else -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                message = "An unexpected error occurred: ${error.message}",
                isCritical = false,
                isRecoverable = true,
                suggestedAction = "Please try again"
            )
        }
    }

    fun logError(
        tag: String,
        message: String,
        error: Throwable? = null
    ) {
        if (error != null) {
            Timber.tag(tag).e(error, message)
        } else {
            Timber.tag(tag).e(message)
        }
    }

    fun logWarning(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    fun logInfo(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun logDebug(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }
}

data class ErrorInfo(
    val type: ErrorType,
    val message: String,
    val isCritical: Boolean,
    val isRecoverable: Boolean,
    val suggestedAction: String
)

enum class ErrorType {
    MEMORY,
    IO,
    NETWORK,
    VALIDATION,
    PERMISSION,
    UNKNOWN
}