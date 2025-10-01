package com.jascanner.presentation.editor.comparison

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.presentation.EditorUiState
import com.jascanner.presentation.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun BeforeAfterComparisonView(
    documentId: String,
    viewModel: EditorViewModel = hiltViewModel()
) {
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is EditorUiState.Success -> {
            val originalBitmap = state.document.pages.firstOrNull()?.originalBitmap
            val modifiedBitmap = state.document.pages.firstOrNull()?.processedBitmap

            var differenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var highlightDifferences by remember { mutableStateOf(true) }

            LaunchedEffect(highlightDifferences, originalBitmap, modifiedBitmap) {
                if (highlightDifferences && originalBitmap != null && modifiedBitmap != null) {
                    try {
                        differenceBitmap = withContext(Dispatchers.Default) {
                            calculateDifferences(originalBitmap, modifiedBitmap)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to calculate differences")
                    }
                }
            }

            if (originalBitmap != null && modifiedBitmap != null) {
                Text("Before/After Comparison Screen for document: $documentId")
                // The actual comparison UI would be implemented here
            } else {
                Text("Loading comparison for document: $documentId...")
            }
        }
        is EditorUiState.Loading -> {
            Text("Loading document...")
        }
        is EditorUiState.Error -> {
            Text("Error: ${state.message}")
        }
    }
}

private suspend fun calculateDifferences(original: Bitmap, modified: Bitmap): Bitmap {
    // Placeholder for actual difference calculation logic
    return modified.copy(modified.config, true)
}