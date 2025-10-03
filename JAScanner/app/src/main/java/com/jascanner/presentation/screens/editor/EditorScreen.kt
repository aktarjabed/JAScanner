package com.jascanner.presentation.screens.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jascanner.presentation.editor.EditorUiState
import com.jascanner.presentation.editor.EditorViewModel
import com.jascanner.utils.safeAsImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val document by viewModel.document.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: "Edit Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is EditorUiState.Loading -> CircularProgressIndicator()
                    is EditorUiState.Error -> Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    is EditorUiState.Ready -> {
                        val page = document?.pages?.getOrNull(currentPageIndex)
                        val bitmap = page?.processedBitmap ?: page?.originalBitmap
                        bitmap?.safeAsImageBitmap()?.let {
                            Image(
                                bitmap = it,
                                contentDescription = "Document Page ${currentPageIndex + 1}"
                            )
                        } ?: CircularProgressIndicator()
                    }
                }
            }

            // Page Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.setCurrentPageIndex(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text("Page ${currentPageIndex + 1} of ${document?.pages?.size ?: 1}")
                IconButton(
                    onClick = { viewModel.setCurrentPageIndex(currentPageIndex + 1) },
                    enabled = document?.let { currentPageIndex < it.pages.size - 1 } ?: false
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                }
            }

            // Editing Tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { viewModel.rotate(-90f) }) {
                    Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left")
                }
                Button(onClick = { viewModel.rotate(90f) }) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
                }
            }
        }
    }
}