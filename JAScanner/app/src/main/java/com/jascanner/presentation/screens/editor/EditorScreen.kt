package com.jascanner.presentation.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.presentation.components.ActionButton
import com.jascanner.presentation.components.ErrorCard
import com.jascanner.presentation.editor.EditorUiState
import com.jascanner.presentation.editor.EditorViewModel
import com.jascanner.utils.safeAsImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Editor") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ActionButton(
                        text = "Save",
                        icon = Icons.Default.Check,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.save()
                        }
                    )
                }
            )
        },
        bottomBar = {
            EditorToolbar(
                onRotateClicked = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.rotate(90f)
                },
                onCropClicked = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.crop()
                },
                onFilterClicked = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.applyFilter()
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = uiState is EditorUiState.Loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator()
            }

            (uiState as? EditorUiState.Error)?.let { errorState ->
                ErrorCard(
                    error = errorState.message,
                    onDismiss = { viewModel.retryLoad() }
                )
            }

            (uiState as? EditorUiState.Ready)?.let {
                 val document by viewModel.document.collectAsState()
                 val currentPageIndex by viewModel.currentPageIndex.collectAsState()
                 val page = document?.pages?.getOrNull(currentPageIndex)
                 val bitmap = page?.processedBitmap ?: page?.originalBitmap

                 if (bitmap != null) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        bitmap.safeAsImageBitmap()?.let { imageBitmap ->
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Document Page",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                 }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    onRotateClicked: () -> Unit,
    onCropClicked: () -> Unit,
    onFilterClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarButton(icon = Icons.Default.RotateRight, label = "Rotate", onClick = onRotateClicked)
            ToolbarButton(icon = Icons.Default.Crop, label = "Crop", onClick = onCropClicked)
            ToolbarButton(icon = Icons.Default.Filter, label = "Filter", onClick = onFilterClicked)
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}