package com.jascanner.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.jascanner.domain.model.EditorStatistics
import com.jascanner.domain.model.MemoryUsage
import com.jascanner.domain.model.StorageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _statistics = MutableStateFlow(createInitialStatistics())
    val statistics: StateFlow<EditorStatistics> = _statistics

    private var activeOperations = 0
    private var successfulOperations = 0L
    private var failedOperations = 0L

    fun startOperation(operationName: String) {
        activeOperations++
        updateStatistics()
        Timber.d("Started operation: $operationName (active: $activeOperations)")
    }

    fun endOperation(operationName: String, success: Boolean) {
        activeOperations = (activeOperations - 1).coerceAtLeast(0)

        if (success) {
            successfulOperations++
        } else {
            failedOperations++
        }

        updateStatistics()
        Timber.d("Ended operation: $operationName (success: $success)")
    }

    fun getMemoryUsage(): MemoryUsage {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        return MemoryUsage(
            maxMemory = maxMemory,
            totalMemory = totalMemory,
            freeMemory = freeMemory,
            usedMemory = usedMemory
        )
    }

    fun getStorageInfo(): StorageInfo {
        val filesDir = context.filesDir
        return StorageInfo(
            totalSpace = filesDir.totalSpace,
            freeSpace = filesDir.freeSpace,
            usableSpace = filesDir.usableSpace
        )
    }

    fun checkMemoryPressure(): MemoryPressure {
        val memoryUsage = getMemoryUsage()
        val usagePercentage = memoryUsage.usagePercentage

        return when {
            usagePercentage > 90 -> MemoryPressure.CRITICAL
            usagePercentage > 75 -> MemoryPressure.HIGH
            usagePercentage > 50 -> MemoryPressure.MODERATE
            else -> MemoryPressure.LOW
        }
    }

    fun requestMemoryCleanup() {
        try {
            System.gc()
            Timber.d("Memory cleanup requested")
        } catch (e: Exception) {
            Timber.e(e, "Failed to request memory cleanup")
        }
    }

    fun checkStorageSpace(): StorageStatus {
        val storageInfo = getStorageInfo()
        val freeSpaceMB = storageInfo.freeSpace / (1024 * 1024)

        return when {
            freeSpaceMB < 100 -> StorageStatus.CRITICAL
            freeSpaceMB < 500 -> StorageStatus.LOW
            else -> StorageStatus.SUFFICIENT
        }
    }

    private fun updateStatistics() {
        _statistics.value = EditorStatistics(
            activeOperations = activeOperations,
            cachedBitmaps = 0, // Would track bitmap cache if implemented
            totalErrors = failedOperations.toInt(),
            successfulOperations = successfulOperations,
            failedOperations = failedOperations,
            memoryUsage = getMemoryUsage(),
            storageInfo = getStorageInfo()
        )
    }

    private fun createInitialStatistics(): EditorStatistics {
        return EditorStatistics(
            activeOperations = 0,
            cachedBitmaps = 0,
            totalErrors = 0,
            successfulOperations = 0,
            failedOperations = 0,
            memoryUsage = getMemoryUsage(),
            storageInfo = getStorageInfo()
        )
    }

    fun getSystemInfo(): SystemInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return SystemInfo(
            totalRAM = memoryInfo.totalMem,
            availableRAM = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold,
            sdkVersion = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )
    }
}

enum class MemoryPressure {
    LOW, MODERATE, HIGH, CRITICAL
}

enum class StorageStatus {
    SUFFICIENT, LOW, CRITICAL
}

data class SystemInfo(
    val totalRAM: Long,
    val availableRAM: Long,
    val isLowMemory: Boolean,
    val threshold: Long,
    val sdkVersion: Int,
    val manufacturer: String,
    val model: String
)