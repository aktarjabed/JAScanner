package com.jascanner.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.presentation.editor.EditorViewModel

@Composable
fun EditorScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.saveCurrentState()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.pauseBackgroundOperations()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.resumeBackgroundOperations()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    viewModel.cleanup()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cleanup()
        }
    }
    Text("Editor Screen for document: $documentId")
}