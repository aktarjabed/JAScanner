package com.jascanner.presentation.screens.thz

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.device.DeviceCapabilitiesDetector
import com.jascanner.scanner.thz.DummyTerahertzScanner
import com.jascanner.scanner.thz.RealUsbThzScanner
import com.jascanner.scanner.thz.TerahertzScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ThzViewModel @Inject constructor(
    private val deviceCapabilities: DeviceCapabilitiesDetector,
    private val dummyScanner: DummyTerahertzScanner,
    private val realScanner: RealUsbThzScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThzUiState())
    val uiState: StateFlow<ThzUiState> = _uiState.asStateFlow()

    private var currentScanner: TerahertzScanner? = null

    init {
        initializeScanner()
    }

    private fun initializeScanner() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInitializing = true)

            try {
                val capabilities = deviceCapabilities.getCapabilities()
                
                // Try real scanner first, fallback to dummy
                currentScanner = if (capabilities.hasTerahertzSupport) {
                    val realInitialized = realScanner.initialize()
                    if (realInitialized) {
                        Timber.i("Using real THz scanner")
                        realScanner
                    } else {
                        Timber.i("Real THz scanner failed, using dummy")
                        dummyScanner.apply { initialize() }
                    }
                } else {
                    Timber.i("No THz hardware support, using dummy scanner")
                    dummyScanner.apply { initialize() }
                }

                val isAvailable = currentScanner?.isAvailable() == true

                _uiState.value = _uiState.value.copy(
                    isInitializing = false,
                    scannerAvailable = isAvailable,
                    isRealScanner = currentScanner is RealUsbThzScanner,
                    error = if (!isAvailable) "THz scanner not available" else null
                )

                // Observe scan progress
                currentScanner?.getScanProgress()?.collect { progress ->
                    _uiState.value = _uiState.value.copy(scanProgress = progress)
                }

            } catch (e: Exception) {
                Timber.e(e, "THz scanner initialization failed")
                _uiState.value = _uiState.value.copy(
                    isInitializing = false,
                    scannerAvailable = false,
                    error = e.message
                )
            }
        }
    }

    fun startScan(settings: TerahertzScanner.ScanSettings = TerahertzScanner.ScanSettings()) {
        viewModelScope.launch {
            val scanner = currentScanner ?: return@launch

            try {
                _uiState.value = _uiState.value.copy(
                    isScanning = true,
                    scanProgress = 0f,
                    error = null,
                    scanResult = null
                )

                val result = scanner.scan(settings)

                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        scanResult = result,
                        message = "THz scan completed successfully"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = result.error ?: "Scan failed"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "THz scan failed")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = e.message
                )
            }
        }
    }

    fun calibrateScanner() {
        viewModelScope.launch {
            val scanner = currentScanner ?: return@launch

            try {
                _uiState.value = _uiState.value.copy(
                    isCalibrating = true,
                    error = null
                )

                val success = scanner.calibrate()

                _uiState.value = _uiState.value.copy(
                    isCalibrating = false,
                    message = if (success) "Calibration completed" else "Calibration failed"
                )
            } catch (e: Exception) {
                Timber.e(e, "THz calibration failed")
                _uiState.value = _uiState.value.copy(
                    isCalibrating = false,
                    error = e.message
                )
            }
        }
    }

    fun updateScanSettings(settings: TerahertzScanner.ScanSettings) {
        _uiState.value = _uiState.value.copy(scanSettings = settings)
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            scanResult = null,
            scanProgress = 0f,
            message = null,
            error = null
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getAnalysisText(): String {
        val result = _uiState.value.scanResult ?: return ""
        val analysis = result.analysis ?: return "No analysis available"

        return buildString {
            appendLine("THz Analysis Results:")
            appendLine("Confidence: ${(analysis.confidence * 100).toInt()}%")
            appendLine()

            if (analysis.materialComposition.isNotEmpty()) {
                appendLine("Materials Detected:")
                analysis.materialComposition.forEach { material ->
                    appendLine("- ${material.material}: ${(material.confidence * 100).toInt()}%")
                }
                appendLine()
            }

            analysis.thickness?.let {
                appendLine("Thickness: ${String.format("%.2f", it)} mm")
            }

            analysis.density?.let {
                appendLine("Density: ${String.format("%.2f", it)} g/cm³")
            }

            analysis.moistureContent?.let {
                appendLine("Moisture: ${String.format("%.1f", it)}%")
            }

            if (analysis.defects.isNotEmpty()) {
                appendLine()
                appendLine("Defects Found:")
                analysis.defects.forEach { defect ->
                    appendLine("- ${defect.type}: ${defect.description}")
                    appendLine("  Location: (${defect.location.x}, ${defect.location.y})")
                    appendLine("  Severity: ${(defect.severity * 100).toInt()}%")
                }
            }
        }
    }

    fun getSpectralDataSummary(): String {
        val result = _uiState.value.scanResult ?: return ""
        val spectral = result.spectralData ?: return "No spectral data available"

        return buildString {
            appendLine("Spectral Data Summary:")
            appendLine("Frequency Range: ${spectral.frequencies.minOrNull()?.let { String.format("%.2f", it) } ?: "N/A"} - ${spectral.frequencies.maxOrNull()?.let { String.format("%.2f", it) } ?: "N/A"} THz")
            appendLine("Resolution: ${String.format("%.3f", spectral.resolution)} THz")
            appendLine("Signal-to-Noise: ${String.format("%.1f", spectral.signalToNoise)} dB")
            appendLine("Data Points: ${spectral.frequencies.size}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentScanner?.shutdown()
    }
}

data class ThzUiState(
    val isInitializing: Boolean = false,
    val scannerAvailable: Boolean = false,
    val isRealScanner: Boolean = false,
    val isScanning: Boolean = false,
    val isCalibrating: Boolean = false,
    val scanProgress: Float = 0f,
    val scanSettings: TerahertzScanner.ScanSettings = TerahertzScanner.ScanSettings(),
    val scanResult: TerahertzScanner.ThzScanResult? = null,
    val error: String? = null,
    val message: String? = null
)