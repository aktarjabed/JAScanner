package com.aktarjabed.jascanner.util

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
        return when {
            isHighEndDevice() -> Size(1920, 1080)
            isMidRangeDevice() -> Size(1280, 720)
            else -> Size(640, 480)
        }
    }

    fun getOptimalFrameRate(): Int {
        return when {
            isHighEndDevice() -> 30
            isMidRangeDevice() -> 20
            else -> 15
        }
    }

    private fun isHighEndDevice(): Boolean {
        return Build.MANUFACTURER.equals("Samsung", ignoreCase = true) &&
                (Build.MODEL.startsWith("SM-S9") ||
                 Build.MODEL.startsWith("SM-S8") ||
                 Build.MODEL.startsWith("SM-S7"))
    }

    private fun isMidRangeDevice(): Boolean {
        return Build.MANUFACTURER.equals("Samsung", ignoreCase = true) &&
                (Build.MODEL.startsWith("SM-S5") ||
                 Build.MODEL.startsWith("SM-S4") ||
                 Build.MODEL.startsWith("SM-S3") ||
                 Build.MODEL.startsWith("SM-S2") ||
                 Build.MODEL.startsWith("SM-S1"))
    }
}