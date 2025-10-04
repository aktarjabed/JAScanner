package com.jascanner.scanner

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jascanner.device.camera.RobustCameraController
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (cameraPermissionState.status.isGranted) {
                FloatingActionButton(
                    onClick = { viewModel.captureImage() },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
                    cameraController = viewModel.cameraController,
                    lifecycleOwner = lifecycleOwner
                )
            } else {
                Text("Camera permission is required to use this feature.", modifier = Modifier.align(Alignment.Center))
            }

            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    snackbarHostState.showSnackbar(error)
                    viewModel.clearError()
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    cameraController: RobustCameraController,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(
        factory = {
            cameraController.initialize(previewView, lifecycleOwner)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}