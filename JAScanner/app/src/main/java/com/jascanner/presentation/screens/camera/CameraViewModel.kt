package com.jascanner.presentation.screens.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.scanner.camera.CameraController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val cameraController: CameraController
) : ViewModel() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    fun onTakePhoto(
        onImageCaptured: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            // This is a placeholder for the actual image capture logic.
            // In a real implementation, this would interact with the CameraController
            // to capture an image and save it to a file.
            // For now, we'll just simulate a successful capture.
            onImageCaptured("path/to/image.jpg")
        }
    }
}