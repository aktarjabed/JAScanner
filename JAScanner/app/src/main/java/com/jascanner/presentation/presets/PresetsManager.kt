package com.jascanner.presentation.presets

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jascanner.domain.model.ImageAdjustments
import com.jascanner.domain.model.ImageFilter
import javax.inject.Inject
import javax.inject.Singleton

data class FilterPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val isEditable: Boolean = true,
    val adjustments: ImageAdjustments
)

@Singleton
class PresetsManager @Inject constructor(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("filter_presets", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val presetsKey = "presets"

    private val defaultPresets = listOf(
        FilterPreset(name = "Original", isEditable = false, adjustments = ImageAdjustments(filter = null, brightness = 0f, contrast = 1f, saturation = 1f, sharpness = 0f)),
        FilterPreset(name = "Grayscale", isEditable = false, adjustments = ImageAdjustments(filter = ImageFilter.GRAYSCALE, brightness = 0f, contrast = 1f, saturation = 0f, sharpness = 0f)),
        FilterPreset(name = "High Contrast", isEditable = false, adjustments = ImageAdjustments(filter = ImageFilter.BLACK_AND_WHITE, brightness = 0.1f, contrast = 1.5f, saturation = 1f, sharpness = 0.2f))
    )

    fun getPresets(): List<FilterPreset> {
        val json = prefs.getString(presetsKey, null)
        return if (json != null) {
            val type = object : TypeToken<List<FilterPreset>>() {}.type
            gson.fromJson(json, type)
        } else {
            defaultPresets
        }
    }

    fun savePresets(presets: List<FilterPreset>) {
        val json = gson.toJson(presets)
        prefs.edit().putString(presetsKey, json).apply()
    }

    fun addPreset(preset: FilterPreset) {
        val currentPresets = getPresets().toMutableList()
        currentPresets.add(preset)
        savePresets(currentPresets)
    }

    fun updatePreset(preset: FilterPreset) {
        val currentPresets = getPresets().toMutableList()
        val index = currentPresets.indexOfFirst { it.id == preset.id }
        if (index != -1 && currentPresets[index].isEditable) {
            currentPresets[index] = preset
            savePresets(currentPresets)
        }
    }

    fun deletePreset(presetId: String) {
        val currentPresets = getPresets().toMutableList()
        val preset = currentPresets.firstOrNull { it.id == presetId }
        if (preset != null && preset.isEditable) {
            currentPresets.removeAll { it.id == presetId }
            savePresets(currentPresets)
        }
    }
}