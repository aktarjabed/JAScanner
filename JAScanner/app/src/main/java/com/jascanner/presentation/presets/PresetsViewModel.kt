package com.jascanner.presentation.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jascanner.data.managers.PresetsManager
import com.jascanner.domain.model.EditingPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class PresetsUiState {
    object Loading : PresetsUiState()
    object Success : PresetsUiState()
    data class Error(val message: String) : PresetsUiState()
}

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val presetsManager: PresetsManager
) : ViewModel() {

    private val _presets = MutableStateFlow<List<EditingPreset>>(emptyList())
    val presets: StateFlow<List<EditingPreset>> = _presets.asStateFlow()

    private val _uiState = MutableStateFlow<PresetsUiState>(PresetsUiState.Loading)
    val uiState: StateFlow<PresetsUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    fun loadPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = PresetsUiState.Loading

                presetsManager.getAllPresetsFlow()
                    .catch { e ->
                        Timber.e(e, "Failed to load presets")
                        _uiState.value = PresetsUiState.Error("Failed to load presets: ${e.message}")
                    }
                    .collect { presetList ->
                        _presets.value = presetList
                        _uiState.value = PresetsUiState.Success
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error loading presets")
                _uiState.value = PresetsUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun savePreset(preset: EditingPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                presetsManager.saveCustomPreset(preset)
                loadPresets()
                Timber.d("Preset saved: ${preset.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save preset")
                _uiState.value = PresetsUiState.Error("Failed to save preset: ${e.message}")
            }
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                presetsManager.deleteCustomPreset(presetId)
                loadPresets()
                Timber.d("Preset deleted: $presetId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete preset")
                _uiState.value = PresetsUiState.Error("Failed to delete preset: ${e.message}")
            }
        }
    }

    fun applyPresetToDocument(preset: EditingPreset, documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Applying preset ${preset.name} to document $documentId")
                // Implementation depends on document repository
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply preset")
                _uiState.value = PresetsUiState.Error("Failed to apply preset: ${e.message}")
            }
        }
    }
}