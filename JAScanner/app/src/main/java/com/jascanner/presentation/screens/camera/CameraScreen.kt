package com.jascanner.presentation.screens.camera

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jascanner.presentation.components.ActionButton

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    onDocumentCaptured: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    when {
        cameraPermissionState.status.isGranted -> {
            CameraContent(
                onNavigateBack = onNavigateBack,
                onDocumentCaptured = onDocumentCaptured
            )
        }
        else -> {
            CameraPermissionRequest(
                onPermissionRequest = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@Composable
private fun CameraContent(
    onNavigateBack: () -> Unit,
    onDocumentCaptured: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    viewModel.cameraController.initialize(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigateBack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        CaptureButton(
            isRecording = isRecording,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onTakePhoto(
                    onImageCaptured = onDocumentCaptured,
                    onError = { /* TODO */ }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun CaptureButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(if (isRecording) 1.2f else 1f)

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .background(Color.White, shape = MaterialTheme.shapes.large)
            .border(4.dp, Color.Black, shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    )
}

@Composable
fun CameraPermissionRequest(
    onPermissionRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission is required to use this feature.")
        Spacer(modifier = Modifier.height(16.dp))
        ActionButton(
            text = "Grant Permission",
            icon = Icons.Default.VpnKey,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPermissionRequest()
            }
        )
    }
}