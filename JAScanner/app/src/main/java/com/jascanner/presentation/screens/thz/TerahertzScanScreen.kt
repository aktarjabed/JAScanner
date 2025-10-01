package com.jascanner.presentation.screens.thz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.presentation.components.*
import com.jascanner.scanner.thz.TerahertzScanner
import com.jascanner.ui.theme.*
import com.jascanner.utils.safeAsImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerahertzScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThzViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("THz Scanner") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.scannerAvailable) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.calibrateScanner()
                        }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Calibrate",
                                tint = if (uiState.isCalibrating) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusCard(
                    title = "Scanner Status",
                    value = when {
                        uiState.isInitializing -> "Initializing..."
                        uiState.scannerAvailable -> "Ready"
                        else -> "Not Available"
                    },
                    icon = when {
                        uiState.isInitializing -> Icons.Default.Sync
                        uiState.scannerAvailable -> Icons.Default.CheckCircle
                        else -> Icons.Default.Error
                    },
                    color = when {
                        uiState.isInitializing -> InfoColor
                        uiState.scannerAvailable -> SuccessColor
                        else -> ErrorColor
                    }
                )
            }

            if (uiState.scannerAvailable) {
                item {
                    InfoChip(
                        text = if (uiState.isRealScanner) "Hardware Scanner" else "Demo Mode",
                        icon = if (uiState.isRealScanner) Icons.Default.Hardware else Icons.Default.Computer,
                        color = if (uiState.isRealScanner) ThzScanColor else WarningColor
                    )
                }
            }

            if (uiState.scannerAvailable && !uiState.isInitializing) {
                item {
                    InfoCard(title = "Scan Controls") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton(
                                text = "Start Scan",
                                icon = Icons.Default.Scanner,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.startScan()
                                },
                                enabled = !uiState.isScanning && !uiState.isCalibrating,
                                isLoading = uiState.isScanning,
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryActionButton(
                                text = "Clear",
                                icon = Icons.Default.Clear,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.clearResults()
                                },
                                enabled = !uiState.isScanning && uiState.scanResult != null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = uiState.isScanning || uiState.isCalibrating) {
                    ProgressCard(
                        title = if (uiState.isScanning) "Scanning in Progress" else "Calibrating Scanner",
                        progress = if (uiState.isScanning) uiState.scanProgress else 0.5f,
                        progressText = if (uiState.isScanning) "${(uiState.scanProgress * 100).toInt()}%" else "Please wait..."
                    )
                }
            }

            item {
                AnimatedVisibility(visible = uiState.scanResult != null, enter = fadeIn(), exit = fadeOut()) {
                    uiState.scanResult?.let { result ->
                        ScanResultsSection(
                            result = result,
                            analysisText = viewModel.getAnalysisText(),
                            spectralSummary = viewModel.getSpectralDataSummary()
                        )
                    }
                }
            }

            item {
                uiState.error?.let {
                    ErrorCard(error = it, onDismiss = viewModel::clearError)
                }
            }

            item {
                uiState.message?.let {
                    SuccessCard(message = it, onDismiss = viewModel::clearMessage)
                }
            }
        }
    }
}

@Composable
private fun ScanResultsSection(
    result: TerahertzScanner.ThzScanResult,
    analysisText: String,
    spectralSummary: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Scan Results",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        result.image?.let { bitmap ->
            InfoCard(title = "THz Image") {
                bitmap.safeAsImageBitmap()?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "THz Scan Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }

        StatusCard(
            title = "Processing Time",
            value = "${result.processingTimeMs} ms",
            icon = Icons.Default.Timer,
            color = InfoColor
        )

        if (analysisText.isNotEmpty()) {
            InfoCard(title = "Analysis Results") {
                Text(analysisText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (spectralSummary.isNotEmpty()) {
            InfoCard(title = "Spectral Data") {
                Text(spectralSummary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        result.analysis?.materialComposition?.let { materials ->
            if (materials.isNotEmpty()) {
                InfoCard(title = "Detected Materials") {
                    materials.forEach { material ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(material.material, style = MaterialTheme.typography.bodyMedium)
                            InfoChip(
                                text = "${(material.confidence * 100).toInt()}%",
                                color = when {
                                    material.confidence > 0.8f -> SuccessColor
                                    material.confidence > 0.6f -> WarningColor
                                    else -> ErrorColor
                                }
                            )
                        }
                    }
                }
            }
        }

        result.analysis?.defects?.let { defects ->
            if (defects.isNotEmpty()) {
                InfoCard(title = "Defects Detected") {
                    defects.forEach { defect ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "${defect.type}: ${defect.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Location: (${defect.location.x}, ${defect.location.y})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Severity: ${(defect.severity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    defect.severity > 0.7f -> ErrorColor
                                    defect.severity > 0.4f -> WarningColor
                                    else -> InfoColor
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}