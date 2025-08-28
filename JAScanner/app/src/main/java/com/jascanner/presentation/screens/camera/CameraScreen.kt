package com.jascanner.presentation.screens.camera

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CameraScreen(onNavigateBack: () -> Unit, onDocumentCaptured: (String) -> Unit) {
    Text("Camera coming soon")
}

