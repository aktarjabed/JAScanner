package com.jascanner.scanner.thz

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

interface TerahertzScanner {
    
    data class ThzScanResult(
        val success: Boolean,
        val image: Bitmap? = null,
        val spectralData: SpectralData? = null,
        val analysis: ThzAnalysis? = null,
        val processingTimeMs: Long,
        val error: String? = null
    )

    data class SpectralData(
        val frequencies: List<Float>, // THz frequencies
        val amplitudes: List<Float>, // Corresponding amplitudes
        val phases: List<Float>, // Phase information
        val timeStamps: List<Long>, // Acquisition timestamps
        val resolution: Float, // Frequency resolution in THz
        val signalToNoise: Float // Signal-to-noise ratio
    )

    data class ThzAnalysis(
        val materialComposition: List<MaterialDetection>,
        val thickness: Float?, // Material thickness in mm
        val density: Float?, // Material density
        val moistureContent: Float?, // Moisture percentage
        val defects: List<DefectDetection>,
        val confidence: Float // Overall analysis confidence
    )

    data class MaterialDetection(
        val material: String,
        val confidence: Float,
        val region: android.graphics.Rect? = null
    )

    data class DefectDetection(
        val type: DefectType,
        val location: android.graphics.Point,
        val severity: Float,
        val description: String
    )

    enum class DefectType {
        CRACK, VOID, DELAMINATION, FOREIGN_OBJECT, THICKNESS_VARIATION, UNKNOWN
    }

    data class ScanSettings(
        val frequencyRange: Pair<Float, Float> = Pair(0.1f, 3.0f), // THz range
        val resolution: Float = 0.01f, // THz resolution
        val integrationTime: Long = 1000L, // ms
        val scanArea: android.graphics.Rect? = null, // Scan region
        val enableSpectroscopy: Boolean = true,
        val enableImaging: Boolean = true,
        val backgroundSubtraction: Boolean = true
    )

    suspend fun initialize(): Boolean
    suspend fun isAvailable(): Boolean
    suspend fun scan(settings: ScanSettings = ScanSettings()): ThzScanResult
    suspend fun calibrate(): Boolean
    fun getScanProgress(): Flow<Float>
    fun shutdown()
}