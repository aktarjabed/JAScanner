package com.jascanner.presentation.batch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jascanner.domain.model.EditableDocument

@Composable
fun BatchOperationsScreen(
    documents: List<EditableDocument>,
    onNavigateBack: () -> Unit
) {
    Text("Batch Operations Screen")
}