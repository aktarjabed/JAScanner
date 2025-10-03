package com.jascanner.core

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorHandler @Inject constructor() {

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