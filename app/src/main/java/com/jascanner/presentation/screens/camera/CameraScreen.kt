package com.jascanner.presentation.screens.camera

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.jascanner.presentation.components.*
import com.jascanner.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    onDocumentCaptured: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Permission handling
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Handle document captured
    LaunchedEffect(uiState.documentSaved) {
        if (uiState.documentSaved && uiState.savedDocumentId > 0) {
            onDocumentCaptured(uiState.savedDocumentId.toString())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !cameraPermissionState.status.isGranted -> {
                PermissionRequestScreen(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onNavigateBack = onNavigateBack,
                    showRationale = cameraPermissionState.status.shouldShowRationale
                )
            }
            
            uiState.isInitializing -> {
                InitializingScreen()
            }
            
            uiState.capturedImage != null -> {
                ImagePreviewScreen(
                    uiState = uiState,
                    onRetake = viewModel::retakePhoto,
                    onNavigateBack = onNavigateBack,
                    onClearMessage = viewModel::clearMessage,
                    onClearError = viewModel::clearError
                )
            }
            
            else -> {
                CameraPreviewScreen(
                    uiState = uiState,
                    onCapture = viewModel::captureImage,
                    onBurstCapture = viewModel::captureBurst,
                    onToggleFlash = viewModel::toggleFlash,
                    onSwitchCamera = viewModel::switchCamera,
                    onZoomChange = viewModel::setZoom,
                    onNavigateBack = onNavigateBack,
                    lifecycleOwner = lifecycleOwner
                )
            }
        }
        
        // Error handling
        uiState.error?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                ErrorCard(
                    error = error,
                    onDismiss = viewModel::clearError
                )
            }
        }
        
        // Success message
        uiState.message?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                SuccessCard(
                    message = message,
                    onDismiss = viewModel::clearMessage
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    showRationale: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (showRationale) {
                "Camera access is required to scan documents. Please grant permission to continue."
            } else {
                "This app needs camera access to scan documents."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Camera Permission")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun InitializingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Initializing Camera...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CameraPreviewScreen(
    uiState: CameraUiState,
    onCapture: () -> Unit,
    onBurstCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onNavigateBack: () -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    previewView = this
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // AR Guide overlay
        ARGuideOverlay(
            isScanning = uiState.isCapturing,
            modifier = Modifier.fillMaxSize()
        )
        
        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CameraControlButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.hasFlash) {
                    CameraControlButton(
                        icon = if (uiState.isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        onClick = onToggleFlash,
                        isActive = uiState.isFlashOn
                    )
                }
                
                CameraControlButton(
                    icon = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    onClick = onSwitchCamera
                )
            }
        }
        
        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Capture controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Burst mode button
                ActionButton(
                    text = "Burst",
                    icon = Icons.Default.BurstMode,
                    onClick = onBurstCapture,
                    enabled = !uiState.isCapturing,
                    isLoading = uiState.isBurstMode
                )
                
                // Main capture button
                CaptureButton(
                    onClick = onCapture,
                    isCapturing = uiState.isCapturing,
                    modifier = Modifier.size(80.dp)
                )
                
                // Settings placeholder
                Box(modifier = Modifier.width(80.dp))
            }
        }
        
        // Processing overlay
        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Processing image...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    
                    if (uiState.processingTimeMs > 0) {
                        Text(
                            text = "${uiState.processingTimeMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewScreen(
    uiState: CameraUiState,
    onRetake: () -> Unit,
    onNavigateBack: () -> Unit,
    onClearMessage: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            
            Text(
                text = "Scan Result",
                style = MaterialTheme.typography.titleLarge
            )
            
            IconButton(onClick = onRetake) {
                Icon(Icons.Default.Refresh, contentDescription = "Retake")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Results
        if (uiState.ocrText.isNotEmpty()) {
            StatusCard(
                title = "Text Recognition",
                value = "${(uiState.ocrConfidence * 100).toInt()}% confidence",
                icon = Icons.Default.TextFields,
                color = if (uiState.ocrConfidence > 0.8f) SuccessColor else WarningColor
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Extracted Text",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = uiState.ocrText.ifBlank { "No text detected" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (uiState.documentSaved) {
            SuccessCard(
                message = "Document saved successfully!",
                onDismiss = onClearMessage
            )
        }
    }
}