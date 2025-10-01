package com.jascanner.presentation.presets

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jascanner.domain.model.EditingPreset

@Composable
fun PresetsScreen(
    onPresetSelected: (EditingPreset) -> Unit,
    onNavigateBack: () -> Unit
) {
    Text("Presets Screen")
}