package com.jascanner.presentation.screens.documents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selectable.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jascanner.export.ExportFormat
import com.jascanner.export.QualityProfile

@Composable
fun DocumentDetailScreen(docId: Long, onBack: () -> Unit, viewModel: DocumentDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    LaunchedEffect(docId) { viewModel.load(docId) }
    val ui by viewModel.ui.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }

    if (showExportDialog) {
        ExportSettingsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { format, quality ->
                viewModel.exportImage(format, quality)
                showExportDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.document?.title ?: "Document", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
                actions = { IconButton(onClick = viewModel::verify) { Icon(Icons.Default.VerifiedUser, contentDescription = null) } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            ui.document?.let { doc ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Path: ${doc.filePath}", style = MaterialTheme.typography.bodyMedium)
                    Text("Created: ${doc.createdAt}", style = MaterialTheme.typography.bodySmall)
                    Text("Modified: ${doc.modifiedAt}", style = MaterialTheme.typography.bodySmall)
                } }
                Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Archival & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::exportPDFA, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Archive, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export PDF/A")
                        }
                        Button(onClick = { /* Provide chain & TSA UI externally */ }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Security, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Add LTV Sign")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export Image")
                    }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Extracted Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = doc.textContent.ifBlank { "No text" })
                } }
            }
            ui.signatureInfo?.let {
                Card { Column(Modifier.padding(12.dp)) { Text("Verification", fontWeight = FontWeight.Bold); Text(it) } }
            }
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ExportSettingsDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, QualityProfile) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.JPG) }
    var selectedQuality by remember { mutableStateOf(QualityProfile.BALANCED) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Export Settings", style = MaterialTheme.typography.titleLarge)

                // Format Selector
                Text("Format", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExportFormat.values().forEach { format ->
                        Row(
                            Modifier.selectable(
                                selected = (format == selectedFormat),
                                onClick = { selectedFormat = format }
                            ).padding(horizontal = 8.dp)
                        ) {
                            RadioButton(selected = (format == selectedFormat), onClick = null)
                            Text(text = format.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // Quality Selector
                Text("Quality", style = MaterialTheme.typography.titleMedium)
                Column {
                    QualityProfile.values().forEach { quality ->
                        Row(
                             Modifier.fillMaxWidth().selectable(
                                selected = (quality == selectedQuality),
                                onClick = { selectedQuality = quality }
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (quality == selectedQuality), onClick = null)
                            Text(text = quality.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selectedFormat, selectedQuality) }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}