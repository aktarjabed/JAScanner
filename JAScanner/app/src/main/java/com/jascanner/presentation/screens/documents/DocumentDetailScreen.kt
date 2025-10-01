package com.jascanner.presentation.screens.documents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.jascanner.presentation.components.ActionButton
import com.jascanner.presentation.components.ErrorCard
import com.jascanner.presentation.components.InfoCard
import com.jascanner.presentation.components.SecondaryActionButton
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: Long,
    onBack: () -> Unit,
    onNavigateToEditor: (Long) -> Unit,
    viewModel: DocumentDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    LaunchedEffect(docId) {
        viewModel.load(docId)
    }
    val uiState by viewModel.ui.collectAsState()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.document?.name ?: "Document Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToEditor(docId)
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }

            AnimatedVisibility(
                visible = !uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.document?.let { doc ->
                        InfoCard(title = "Document Info") {
                            Text("Created: ${doc.createdAt.toFormattedDate()}", style = MaterialTheme.typography.bodySmall)
                            Text("Modified: ${doc.modifiedAt.toFormattedDate()}", style = MaterialTheme.typography.bodySmall)
                        }

                        InfoCard(title = "Actions") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionButton(
                                    text = "Export PDF/A",
                                    icon = Icons.Default.Archive,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.exportPDFA()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryActionButton(
                                    text = "Add LTV Sign",
                                    icon = Icons.Default.Security,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        /* TODO */
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    uiState.signatureInfo?.let {
                        InfoCard(title = "Verification Status") {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    uiState.error?.let {
                        ErrorCard(error = it, onDismiss = { viewModel.clearError() })
                    }

                    uiState.message?.let {
                        SnackbarHost(
                            hostState = remember { SnackbarHostState() }
                                .apply {
                                    LaunchedEffect(it) {
                                        showSnackbar(
                                            message = it,
                                            duration = SnackbarDuration.Short
                                        )
                                        viewModel.clearMessage()
                                    }
                                },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

private fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}