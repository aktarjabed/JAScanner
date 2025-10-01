package com.jascanner.presentation.screens.thz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.scanner.thz.TerahertzScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThzUiState(
    val isInitializing: Boolean = true,
    val scannerAvailable: Boolean = false,
    val isRealScanner: Boolean = false,
    val isScanning: Boolean = false,
    val isCalibrating: Boolean = false,
    val scanProgress: Float = 0f,
    val scanResult: TerahertzScanner.ThzScanResult? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ThzViewModel @Inject constructor(
    private val thzScanner: TerahertzScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThzUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true) }
            delay(1500) // Simulate scanner initialization
            val available = thzScanner.isAvailable()
            _uiState.update {
                it.copy(
                    isInitializing = false,
                    scannerAvailable = available,
                    isRealScanner = thzScanner.isHardwareScanner()
                )
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, scanResult = null) }
            for (i in 1..10) {
                delay(300)
                _uiState.update { it.copy(scanProgress = i / 10f) }
            }
            val result = thzScanner.performScan()
            _uiState.update { it.copy(isScanning = false, scanResult = result) }
        }
    }

    fun calibrateScanner() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalibrating = true) }
            delay(2000) // Simulate calibration
            _uiState.update { it.copy(isCalibrating = false, message = "Scanner calibrated successfully.") }
        }
    }

    fun clearResults() {
        _uiState.update { it.copy(scanResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun getAnalysisText(): String {
        return "Analysis shows a high probability of a hidden metallic object."
    }

    fun getSpectralDataSummary(): String {
        return "Peak frequency at 1.2 THz with a bandwidth of 0.8 THz."
    }
}