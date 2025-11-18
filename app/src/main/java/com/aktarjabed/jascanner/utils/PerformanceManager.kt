package com.aktarjabed.jascanner.utils

import android.os.Build
import android.util.Size

object PerformanceManager {

    fun getOptimalAnalysisResolution(): Size {
        return when {
            isHighEndDevice() -> Size(1920, 1080)
            isMidRangeDevice() -> Size(1280, 720)
            else -> Size(640, 480)
        }
    }

    fun getOptimalPreviewResolution(): Size {
        return getOptimalAnalysisResolution()
    }

    fun getOptimalFrameRate(): Int {
        return when {
            isHighEndDevice() -> 30
            isMidRangeDevice() -> 20
            else -> 15
        }
    }

    private fun isHighEndDevice(): Boolean {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Runtime.getRuntime().availableProcessors() >= 8)
    }

    private fun isMidRangeDevice(): Boolean {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Runtime.getRuntime().availableProcessors() >= 4)
    }
}