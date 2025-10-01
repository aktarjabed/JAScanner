package com.jascanner.data.managers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jascanner.domain.model.EditingPreset
import com.jascanner.domain.model.ImageAdjustments
import com.jascanner.domain.model.ImageFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("filter_presets", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val presetsKey = "presets"

    private val _presets = MutableStateFlow<List<EditingPreset>>(emptyList())
    val presets = _presets.asStateFlow()

    private val defaultPresets = listOf(
        EditingPreset(name = "Original", isEditable = false, adjustments = ImageAdjustments(filter = null, brightness = 0f, contrast = 1f, saturation = 1f, sharpness = 0f)),
        EditingPreset(name = "Grayscale", isEditable = false, adjustments = ImageAdjustments(filter = ImageFilter.GRAYSCALE, brightness = 0f, contrast = 1f, saturation = 0f, sharpness = 0f)),
        EditingPreset(name = "High Contrast", isEditable = false, adjustments = ImageAdjustments(filter = ImageFilter.BLACK_AND_WHITE, brightness = 0.1f, contrast = 1.5f, saturation = 1f, sharpness = 0.2f))
    )

    init {
        loadPresets()
    }

    private fun loadPresets() {
        val json = prefs.getString(presetsKey, null)
        _presets.value = if (json != null) {
            val type = object : TypeToken<List<EditingPreset>>() {}.type
            gson.fromJson(json, type)
        } else {
            defaultPresets
        }
    }

    fun saveCustomPreset(preset: EditingPreset) {
        val currentPresets = _presets.value.toMutableList()
        currentPresets.add(preset)
        _presets.value = currentPresets
        savePresetsToPrefs()
    }

    fun updatePreset(preset: EditingPreset) {
        val currentPresets = _presets.value.toMutableList()
        val index = currentPresets.indexOfFirst { it.id == preset.id }
        if (index != -1 && currentPresets[index].isEditable) {
            currentPresets[index] = preset
            _presets.value = currentPresets
            savePresetsToPrefs()
        }
    }

    fun deleteCustomPreset(presetId: String) {
        val currentPresets = _presets.value.toMutableList()
        val preset = currentPresets.firstOrNull { it.id == presetId }
        if (preset != null && preset.isEditable) {
            currentPresets.removeAll { it.id == presetId }
            _presets.value = currentPresets
            savePresetsToPrefs()
        }
    }

    private fun savePresetsToPrefs() {
        val json = gson.toJson(_presets.value)
        prefs.edit().putString(presetsKey, json).apply()
    }

    fun getAllPresetsFlow(): Flow<List<EditingPreset>> {
        return presets
    }
}