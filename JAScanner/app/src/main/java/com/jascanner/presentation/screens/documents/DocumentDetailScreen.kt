package com.jascanner.presentation.screens.documents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.filled.Share

@Composable
fun DocumentDetailScreen(
    docId: Long,
    onBack: () -> Unit,
    onNavigateToCompressionSettings: (Long, Int, Long) -> Unit,
    viewModel: DocumentDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    LaunchedEffect(docId) { viewModel.load(docId) }
    val ui by viewModel.ui.collectAsState()
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
                    Button(
                        onClick = { onNavigateToCompressionSettings(docId, ui.pageCount, ui.originalSize) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.document != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export")
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

