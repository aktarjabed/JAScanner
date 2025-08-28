package com.jascanner.presentation.screens.thz

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.presentation.components.*
import com.jascanner.scanner.thz.TerahertzScanner
import com.jascanner.ui.theme.*

@Composable
fun TerahertzScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThzViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "THz Scanner", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.scannerAvailable) {
                        IconButton(onClick = viewModel::calibrateScanner) {
                            Icon(
                                Icons.Default.Tune, 
                                contentDescription = "Calibrate",
                                tint = if (uiState.isCalibrating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scanner status
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
            
            // Scanner type
            if (uiState.scannerAvailable) {
                InfoChip(
                    text = if (uiState.isRealScanner) "Hardware Scanner" else "Demo Mode",
                    icon = if (uiState.isRealScanner) Icons.Default.Hardware else Icons.Default.Computer,
                    color = if (uiState.isRealScanner) ThzScanColor else WarningColor
                )
            }
            
            // Scan controls
            if (uiState.scannerAvailable && !uiState.isInitializing) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Scan Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionButton(
                                text = "Start Scan",
                                icon = Icons.Default.Scanner,
                                onClick = { viewModel.startScan() },
                                enabled = !uiState.isScanning && !uiState.isCalibrating,
                                isLoading = uiState.isScanning,
                                modifier = Modifier.weight(1f)
                            )
                            
                            ActionButton(
                                text = "Clear",
                                icon = Icons.Default.Clear,
                                onClick = viewModel::clearResults,
                                enabled = !uiState.isScanning && uiState.scanResult != null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Scan progress
            if (uiState.isScanning) {
                ProgressCard(
                    title = "Scanning in Progress",
                    progress = uiState.scanProgress,
                    progressText = "${(uiState.scanProgress * 100).toInt()}%"
                )
            }
            
            // Calibration status
            if (uiState.isCalibrating) {
                ProgressCard(
                    title = "Calibrating Scanner",
                    progress = 0.5f, // Indeterminate progress
                    progressText = "Please wait..."
                )
            }
            
            // Scan results
            uiState.scanResult?.let { result ->
                ScanResultsSection(
                    result = result,
                    analysisText = viewModel.getAnalysisText(),
                    spectralSummary = viewModel.getSpectralDataSummary()
                )
            }
            
            // Error display
            uiState.error?.let { error ->
                ErrorCard(
                    error = error,
                    onDismiss = viewModel::clearError
                )
            }
            
            // Success message
            uiState.message?.let { message ->
                SuccessCard(
                    message = message,
                    onDismiss = viewModel::clearMessage
                )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Scan Results",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Scan image
        result.image?.let { bitmap ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "THz Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "THz Scan Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }
        
        // Processing time
        StatusCard(
            title = "Processing Time",
            value = "${result.processingTimeMs} ms",
            icon = Icons.Default.Timer,
            color = InfoColor
        )
        
        // Analysis results
        if (analysisText.isNotEmpty()) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Analysis Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = analysisText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Spectral data summary
        if (spectralSummary.isNotEmpty()) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Spectral Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = spectralSummary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Material detection
        result.analysis?.materialComposition?.let { materials ->
            if (materials.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Detected Materials",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        materials.forEach { material ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = material.material,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
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
        }
        
        // Defects
        result.analysis?.defects?.let { defects ->
            if (defects.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningColor,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "Defects Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WarningColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        defects.forEach { defect ->
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
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
}