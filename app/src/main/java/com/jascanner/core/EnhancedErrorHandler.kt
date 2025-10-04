package com.jascanner.core

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedErrorHandler @Inject constructor() {

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val cause: Exception? = null) : Result<Nothing>()
    }

    suspend fun <T> safeExecuteAsync(operationName: String, block: suspend () -> T): Result<T> {
        return try {
            Result.Success(block())
        } catch (e: Exception) {
            val errorMessage = "Operation failed: $operationName"
            Timber.e(e, errorMessage)
            recordException(e, errorMessage)
            Result.Error(errorMessage, e)
        }
    }

    fun recordException(throwable: Throwable, message: String? = null) {
        if (message != null) {
            Timber.e(throwable, message)
        } else {
            Timber.e(throwable)
        }
        // In a real app, this would integrate with a crash reporting service like Crashlytics.
    }

    fun logWarning(message: String) {
        Timber.w(message)
    }

    fun logInfo(message: String) {
        Timber.i(message)
    }
}