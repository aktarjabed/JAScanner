package com.jascanner.presentation.screens.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jascanner.data.entities.DocumentEntity

@Composable
fun DocumentListScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToThz: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDocumentSelected: (Long) -> Unit,
    viewModel: DocumentListViewModel
) {
    val ui by viewModel.ui.collectAsState()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCamera) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (ui.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.documents) { doc ->
                    Card(Modifier.fillMaxWidth().clickable { onDocumentSelected(doc.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                            Text(doc.filePath, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

