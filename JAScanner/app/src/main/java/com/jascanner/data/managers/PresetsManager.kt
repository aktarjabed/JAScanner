package com.jascanner.data.managers

import com.jascanner.domain.model.EditingPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Implement the logic for saving, loading, and deleting presets from a data source.
@Singleton
class PresetsManager @Inject constructor() {

    fun getAllPresetsFlow(): Flow<List<EditingPreset>> {
        return flowOf(emptyList())
    }

    fun saveCustomPreset(preset: EditingPreset) {
        // Implementation needed
    }

    fun deleteCustomPreset(presetId: String) {
        // Implementation needed
    }
}